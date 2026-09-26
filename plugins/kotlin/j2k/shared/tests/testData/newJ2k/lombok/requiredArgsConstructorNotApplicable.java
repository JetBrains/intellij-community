// !ADD_LOMBOK_ANNOTATIONS
// KTIJ-19423: Lombok changes the generated constructor here, so J2K must keep the annotation
package test;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(staticName = "of")
class StaticNameFacade {
    private final ServiceA serviceA;
}

@RequiredArgsConstructor
class FacadeWithoutRequiredFields {
    private ServiceA serviceA;

    private final String initialized = "x";
}

class ServiceA {
}
