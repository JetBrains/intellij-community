package internalMethod

fun main() {
    val e = Example()
    e.foo()
}

class Example {
    fun foo() {
        //Breakpoint!
        boo()
    }

    internal fun boo() = Unit
}
