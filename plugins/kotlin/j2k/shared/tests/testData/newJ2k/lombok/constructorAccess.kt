// !ADD_LOMBOK_ANNOTATIONS
// KTIJ-19423: the access level of a Lombok constructor becomes the visibility of the Kotlin constructor
package test

class PackageFacade internal constructor(private val service: Service?)

internal class PrivateHolder private constructor(private val name: String?)

internal class NoConstructor {
    private lateinit var service: Service
}

internal class Service
