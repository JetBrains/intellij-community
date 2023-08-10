// BIND_TO B
class A<T> { }

class B<T> { }

fun foo() {
    val x = <caret>A<Any>()
}