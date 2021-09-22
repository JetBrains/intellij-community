// API_VERSION: 1.6
// RUNTIME_WITH_STDLIB_JDK8

import java.lang.annotation.Repeatable;

@Repeatable(AContainer.class)
@interface A {
    int value();
}

@interface AContainer {
    A[] value();
}