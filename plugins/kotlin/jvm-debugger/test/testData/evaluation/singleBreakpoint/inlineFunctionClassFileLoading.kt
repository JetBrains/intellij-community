// This test used to confuse the inliner during evaluate expression.
//
// The test creates an unused module that has a dependency on a different
// version of the same library that the main module uses. It used to be
// the case that evaluate expression could pick up inline functions from
// the library via this unused module instead of from the actual module
// dependency, leading to incorrect results or crashes during compilation.
//
// See KTIJ-25391.

// MODULE: a
// PLATFORM: jvm
// FILE: a.kt
//
// The following directive attaches the `inline1` library to the IDE module for
// `a`, but does not add that library to the classpath for initial
// compilation for the main module `jvm` below.
//
// ATTACH_LIBRARY_TO_IDE_MODULE: inline1

package b

// MODULE: jvm
// PLATFORM: jvm
// FILE: jvm.kt
// ATTACH_LIBRARY: inline2

package c
import test.inline.foo

fun main() {
    // EXPRESSION: foo()
    // RESULT: 2: I
    //Breakpoint!
    return
}
