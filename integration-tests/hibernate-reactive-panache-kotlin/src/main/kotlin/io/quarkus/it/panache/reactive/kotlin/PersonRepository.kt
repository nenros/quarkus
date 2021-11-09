package io.quarkus.it.panache.reactive.kotlin

import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepository
import io.quarkus.hibernate.orm.panache.kotlin.runtime.KotlinJpaOperations.Companion.INSTANCE
import io.quarkus.hibernate.reactive.panache.kotlin.PanacheRepository
import io.quarkus.hibernate.reactive.panache.kotlin.runtime.KotlinJpaOperations.Companion.INSTANCE
import javax.enterprise.context.ApplicationScoped

@ApplicationScoped
open class PersonRepository : PanacheRepository<Person> {
    override fun count(query: String, params: Map<String, Any>): Long {
        return INSTANCE.count(Person::class.java, query, params)
    }
}
