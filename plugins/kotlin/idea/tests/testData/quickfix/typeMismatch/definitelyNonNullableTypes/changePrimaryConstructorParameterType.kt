// "Change parameter 'x' type of primary constructor of class 'Foo' to 'T & Any'" "true"
// LANGUAGE_VERSION: 1.8
package a

class Foo<T>(val x: T) {
    fun foo(y: T & Any) {}
}

fun <T> Foo<T>.bar(z: T) {
    foo(x<caret>)
}
