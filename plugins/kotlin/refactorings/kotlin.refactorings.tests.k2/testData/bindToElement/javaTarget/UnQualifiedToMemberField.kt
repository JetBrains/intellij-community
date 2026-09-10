// FILE: test/UnQualified.kt
// BIND_TO test.Container.field
package test

import test.foo.fooBar
import test.Container

fun Container.caller() {
    <caret>field
}

// FILE: test/foo/Test.kt
package test.foo


val field = "old property"

// FILE: test/Container.java
package test;

public class Container {
    public String field = "new field";
}
