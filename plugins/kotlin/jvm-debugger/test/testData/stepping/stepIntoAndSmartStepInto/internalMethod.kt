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

// IGNORE_K2_SMART_STEP_INTO
// Remove after IDEA-326256 fix
