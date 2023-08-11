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

// EXPRESSION: block { d.foo() }
// RESULT: 2: J

// EXPRESSION: block { (d as Base).foo() }
// RESULT: 2: J

// EXPRESSION: block { b.foo() }
// RESULT: 1: J

// EXPRESSION: block { b.foo() }
// RESULT: 1: J

// EXPRESSION: block { b.x }
// RESULT: 0: J

// EXPRESSION: block { d.x }
// RESULT: "": Ljava/lang/String;

// EXPRESSION: block { (d as Base).x }
// RESULT: 0: J

// IGNORE_K2