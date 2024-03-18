// BULK
// SHORTEN: test.A dependency.A
package test

class A

fun usage(a: dependency.A) {}

fun usage(a: test.A) {}