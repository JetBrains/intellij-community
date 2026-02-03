// PARAM_TYPES: kotlin.String, kotlin.Comparable<kotlin.String>, kotlin.CharSequence, java.io.Serializable, kotlin.Any], [kotlin.String, kotlin.Comparable<kotlin.String>, kotlin.CharSequence, java.io.Serializable, kotlin.Any
// PARAM_DESCRIPTOR: value-parameter a: kotlin.String defined in p.B.m, value-parameter b: kotlin.String defined in p.B.m
package p

import p.A.foo

class A {
    fun foo(i1: Int, i2: Int) {}
    fun foo(s1: String, s2: String) {}
}

class B {
    fun m(a: String, b: String) {
        <selection>foo(a, b)</selection>
    }
}