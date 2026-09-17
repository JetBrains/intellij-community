// !ADD_LOMBOK_ANNOTATIONS
// KTIJ-19423: Lombok writes the constructor, so the converted class loses it without this rule
package test;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class Facade {
    private final ServiceA serviceA;
    private final ServiceB serviceB;

    @NonNull
    private ServiceC serviceC;

    private final String initialized = "x";

    private static final String CONSTANT = "c";

    private ServiceD notRequired;

    public static class ServiceA {}

    public static class ServiceB {}

    public static class ServiceC {}

    public static class ServiceD {}
}

@RequiredArgsConstructor
class FacadeWithOwnConstructor {
    private final Facade.ServiceA serviceA;

    FacadeWithOwnConstructor() {
        this.serviceA = null;
    }
}
