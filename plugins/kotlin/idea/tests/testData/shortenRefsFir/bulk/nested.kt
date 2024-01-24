// BULK
// SHORTEN: test.A dependency.A
package test

import dependency.B

class A(b: B? = null)

fun usage() {
    val testA = test.A(B(test.A(), dependency.A()))
    val depA = dependency.A(B(test.A(), dependency.A()))
}