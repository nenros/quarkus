package io.quarkus.it.panache.reactive.kotlin

import javax.enterprise.context.ApplicationScoped

@ApplicationScoped
open class Bug5274EntityRepository : AbstractRepository<Person>()