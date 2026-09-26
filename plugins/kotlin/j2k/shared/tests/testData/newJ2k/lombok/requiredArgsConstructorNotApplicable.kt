// !ADD_LOMBOK_ANNOTATIONS
// KTIJ-19423: Lombok changes the generated constructor here, so J2K must keep the annotation
package test

import lombok.RequiredArgsConstructor

@RequiredArgsConstructor(staticName = "of")
internal class StaticNameFacade {
    private val serviceA: ServiceA? = null
}

internal class FacadeWithoutRequiredFields {
    private val serviceA: ServiceA? = null

    private val initialized = "x"
}

internal class ServiceA
