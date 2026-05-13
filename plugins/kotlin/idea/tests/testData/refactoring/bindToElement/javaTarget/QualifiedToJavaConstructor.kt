// FILE: test/UnQualified.kt
// BIND_TO test.Container
package test

fun caller() {
    test.foo.<caret>Container()
}

// FILE: test/foo/Test.kt
package test.foo

class Container

// FILE: test/Container.java
package test;

public class Container {
    public Container() {}
}
