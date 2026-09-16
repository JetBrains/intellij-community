// FILE: test/UnQualified.kt
// BIND_TO test.Container.fooBar
package test

import test.foo.fooBar

fun caller() {
    <caret>foobar
}

// FILE: test/foo/Test.kt
package test.foo

val fooBar = "asdf"

// FILE: test/Container.java
package test;

public class Container {
    public static final String fooBar = "asdf";
}
