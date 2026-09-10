// FILE: test/UnQualified.kt
// BIND_TO test.Container.FOO
package test

import test.foo.Container

fun caller() {
    Container.<caret>FOO
}

// FILE: test/foo/Test.kt
package test.foo

enum class Container {
    FOO, BAR
}

// FILE: test/Container.java
package test;

public enum Container {
    FOO, BAR
}
