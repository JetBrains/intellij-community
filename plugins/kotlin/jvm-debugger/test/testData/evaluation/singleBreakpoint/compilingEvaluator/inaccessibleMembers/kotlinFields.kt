package foo

object Obj {
    private var x: Double = 1.0
    private var y: Short = 2
}

class Cl {
    companion object {
        private var x: Float = 3f
        private var y: Byte = 4
    }

    private val z: Long = 5
    private val s = "s"
}

interface Intf {
    companion object {
        private const val c = 1
    }
}

fun <T> block(block: () -> T): T {
    return block()
}

fun main() {
    val cl = Cl()
    //Breakpoint!
    val x = 0
}

// EXPRESSION: block { Obj.x = 2.0 }
// RESULT: VOID_VALUE

// EXPRESSION: block { Obj.x }
// RESULT: 2.0: D

// EXPRESSION: block { Obj.y = 4 }
// RESULT: VOID_VALUE

// EXPRESSION: block { Obj.y }
// RESULT: 4: S

// EXPRESSION: block { cl.z = 10 }
// RESULT: VOID_VALUE

// EXPRESSION: block { cl.s = "ss" }
// RESULT: VOID_VALUE

// EXPRESSION: block { cl.s }
// RESULT: "ss": Ljava/lang/String;

// EXPRESSION: block { Cl.x = 6f }
// RESULT: VOID_VALUE

// EXPRESSION: block { Cl.x }
// RESULT: 6.0: F

// EXPRESSION: block { Cl.y = 8 }
// RESULT: VOID_VALUE

// EXPRESSION: block { Cl.y }
// RESULT: 8: B

// EXPRESSION: block { Cl.Companion.x }
// RESULT: 6.0: F

// EXPRESSION: block { Intf.c }
// RESULT: 1: I

// IGNORE_K2