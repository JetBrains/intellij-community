// FILE: test/UnQualified.kt
// BIND_TO test.Container.fooBar
package test

import test.foo.fooBar
import test.Container

fun Container.caller() {
    <caret>fooBar()
}

// FILE: test/foo/Test.kt
package test.foo

fun fooBar() { }

// FILE: test/Container.java
package test;

public class Container {
    public void fooBar() { }
}
