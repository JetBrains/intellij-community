open class Base {
    private val x: Long = 0
    protected open fun foo(): Long = 1
}

class Derived : Base() {
    private val x: String = ""
    override fun foo(): Long = 2
}

fun <T> block(block: () -> T): T {
    return block()
}

fun main() {
    val b = Base()
    val d = Derived()
    //Breakpoint!
    val f = 0
}

// Working as intended on EE-IR: No support for disabling reflective access

// REFLECTION_PATCHING: false

// EXPRESSION: block { d.foo() }
// RESULT: Method threw 'java.lang.IllegalAccessError' exception.

// EXPRESSION: block { (d as Base).foo() }
// RESULT: Method threw 'java.lang.IllegalAccessError' exception.

// EXPRESSION: block { b.foo() }
// RESULT: Method threw 'java.lang.IllegalAccessError' exception.

// EXPRESSION: block { b.foo() }
// RESULT: Method threw 'java.lang.IllegalAccessError' exception.

// EXPRESSION: block { b.x }
// RESULT: Method threw 'java.lang.IllegalAccessError' exception.

// EXPRESSION: block { d.x }
// RESULT: Method threw 'java.lang.IllegalAccessError' exception.

// EXPRESSION: block { (d as Base).x }
// RESULT: Method threw 'java.lang.IllegalAccessError' exception.