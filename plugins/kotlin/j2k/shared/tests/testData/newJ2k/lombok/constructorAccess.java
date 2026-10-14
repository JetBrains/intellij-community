// !ADD_LOMBOK_ANNOTATIONS
// KTIJ-19423: the access level of a Lombok constructor becomes the visibility of the Kotlin constructor
package test;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class PackageFacade {
    private final Service service;
}

@AllArgsConstructor(access = AccessLevel.PRIVATE)
class PrivateHolder {
    private String name;
}

@RequiredArgsConstructor(access = AccessLevel.NONE)
class NoConstructor {
    @NonNull
    private Service service;
}

class Service {
}
