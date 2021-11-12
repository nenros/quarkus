package io.quarkus.gradle.dependency;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ExternalDependency;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.artifacts.dsl.DependencyHandler;

import io.quarkus.gradle.tooling.ToolingUtils;

public class ApplicationDeploymentClasspathBuilder {

    private final Project project;
    private final Set<ModuleVersionIdentifier> alreadyProcessed = new HashSet<>();

    public ApplicationDeploymentClasspathBuilder(Project project) {
        this.project = project;
    }

    public synchronized void createBuildClasspath(Collection<ExtensionDependency> knownExtensions,
            String baseConfigurationName) {
        String deploymentConfigurationName = ToolingUtils.toDeploymentConfigurationName(baseConfigurationName);
        project.getConfigurations().create(deploymentConfigurationName);
        DependencyHandler dependencies = project.getDependencies();

        Set<ExtensionDependency> extensions = collectFirstMetQuarkusExtensions(
                DependencyUtils.duplicateConfiguration(project, project.getConfigurations().getByName(baseConfigurationName)),
                knownExtensions);

        // Add conditional extensions
        for (ExtensionDependency knownExtension : knownExtensions) {
            if (knownExtension.isConditional) {
                extensions.add(knownExtension);
            }
        }
        for (ExtensionDependency extension : extensions) {
            if (extension instanceof LocalExtensionDependency) {
                addLocalDeploymentDependency(deploymentConfigurationName, (LocalExtensionDependency) extension, dependencies);
            } else {
                requireDeploymentDependency(deploymentConfigurationName, extension, dependencies);
                if (!alreadyProcessed.add(extension.extensionId)) {
                    continue;
                }
                extension.installDeploymentVariant(dependencies);
            }
        }
    }

    private Set<ExtensionDependency> collectFirstMetQuarkusExtensions(Configuration configuration,
            Collection<ExtensionDependency> knownExtensions) {

        Set<ExtensionDependency> firstLevelExtensions = new HashSet<>();
        Set<ResolvedDependency> firstLevelModuleDependencies = configuration.getResolvedConfiguration()
                .getFirstLevelModuleDependencies();

        Set<String> visitedArtifacts = new HashSet<>();
        for (ResolvedDependency firstLevelModuleDependency : firstLevelModuleDependencies) {
            firstLevelExtensions
                    .addAll(collectQuarkusExtensions(firstLevelModuleDependency, visitedArtifacts, knownExtensions));
        }
        return firstLevelExtensions;
    }

    private Set<ExtensionDependency> collectQuarkusExtensions(ResolvedDependency dependency, Set<String> visitedArtifacts,
            Collection<ExtensionDependency> knownExtensions) {
        String artifactKey = String.format("%s:%s", dependency.getModuleGroup(), dependency.getModuleName());
        if (visitedArtifacts.contains(artifactKey)) {
            return Collections.emptySet();
        } else {
            visitedArtifacts.add(artifactKey);
        }

        Set<ExtensionDependency> extensions = new LinkedHashSet<>();
        ExtensionDependency extension = getExtensionOrNull(dependency.getModuleGroup(), dependency.getModuleName(),
                dependency.getModuleVersion(), knownExtensions);
        if (extension != null) {
            extensions.add(extension);
        } else {
            for (ResolvedDependency child : dependency.getChildren()) {
                extensions.addAll(collectQuarkusExtensions(child, visitedArtifacts, knownExtensions));
            }
        }
        return extensions;
    }

    private void addLocalDeploymentDependency(String deploymentConfigurationName, LocalExtensionDependency extension,
            DependencyHandler dependencies) {
        dependencies.add(deploymentConfigurationName,
                dependencies.project(Collections.singletonMap("path", extension.findDeploymentModulePath())));
    }

    private void requireDeploymentDependency(String deploymentConfigurationName, ExtensionDependency extension,
            DependencyHandler dependencies) {
        ExternalDependency dependency = (ExternalDependency) dependencies.add(deploymentConfigurationName,
                extension.asDependencyNotation());
        dependency.capabilities(
                handler -> handler.requireCapability(DependencyUtils.asCapabilityNotation(extension.deploymentModule)));
    }

    private ExtensionDependency getExtensionOrNull(String group, String artifact, String version,
            Collection<ExtensionDependency> knownExtensions) {
        for (ExtensionDependency knownExtension : knownExtensions) {
            if (group.equals(knownExtension.getGroup()) && artifact.equals(knownExtension.getName())
                    && version.equals(knownExtension.getVersion())) {
                return knownExtension;
            }
        }
        return null;
    }
}
