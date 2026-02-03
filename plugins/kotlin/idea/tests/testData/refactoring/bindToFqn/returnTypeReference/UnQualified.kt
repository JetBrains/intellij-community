// BIND_TO test.B
package test

interface A { }

class B : A { }

fun foo(): <caret>A = B()