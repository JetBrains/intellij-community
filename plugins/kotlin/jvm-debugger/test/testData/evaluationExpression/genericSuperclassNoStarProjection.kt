package genericSuperclassNoStarProjection

interface I

open class B<T> : I {
    val myValB = 200
}

class C : B<Int>()

fun main() {
    val i: I = C()
    //Breakpoint!
    println()
}

// The runtime type is not generic, only its superclass is: no star projection.
// VARIABLE_PATH: i.myValB
// EVAL_EXPRESSION: (i as C).myValB
