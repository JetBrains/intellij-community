// !ADD_LOMBOK_ANNOTATIONS
// KTIJ-19423: Lombok writes the constructor, so the converted class loses it without this rule
package test

import test.Facade.ServiceA

class Facade(private val serviceA: ServiceA, private val serviceB: ServiceB, private val serviceC: ServiceC) {
    private val initialized = "x"

    private val notRequired: ServiceD? = null

    class ServiceA

    class ServiceB

    class ServiceC

    class ServiceD
    companion object {
        private const val CONSTANT = "c"
    }
}

internal class FacadeWithOwnConstructor {
    private val serviceA: ServiceA?

    constructor() {
        this.serviceA = null
    }

    constructor(serviceA: ServiceA?) {
        this.serviceA = serviceA
    }
}
