interface Iface {
    val name: String
    fun foo() = 43
}

interface First {
    val a: String
    val shared: String
}
interface Second {
    val b: String
    val shared: String
}

open class NamedBase(val inherited: String)

fun main() {
    val iface: Iface = object : Iface {
        override val name = "anon"
    }
    val two: Second = object : First, Second {
        override val a = "ay"
        override val b = "bee"
        override val shared = "shared"
    }
    val reversed: Second = object : Second, First {
        override val a = "ay"
        override val b = "bee"
        override val shared = "shared"
    }
    val any: Any = object : First, Second {
        override val a = "ay"
        override val b = "bee"
        override val shared = "shared"
    }
    val anyReversed: Any = object : Second, First {
        override val a = "ay"
        override val b = "bee"
        override val shared = "shared"
    }
    open class LocalBase : NamedBase("base")
    val viaLocalBase: Any = object : LocalBase() {}
    //Breakpoint!
    println(listOf(iface, two, reversed, any, anyReversed, viaLocalBase))
}

// VARIABLE_PATH: iface.name
// EVAL_EXPRESSION: iface.name
// EVAL_RESULT: anon

// VARIABLE_PATH: two.shared
// EVAL_EXPRESSION: two.shared
// EVAL_RESULT: shared

// VARIABLE_PATH: two.b
// EVAL_EXPRESSION: two.b
// EVAL_RESULT: bee

// VARIABLE_PATH: two.a
// EVAL_EXPRESSION: (two as First).a
// EVAL_RESULT: ay

// VARIABLE_PATH: reversed.a
// EVAL_EXPRESSION: (reversed as First).a
// EVAL_RESULT: ay

// VARIABLE_PATH: reversed.b
// EVAL_EXPRESSION: reversed.b
// EVAL_RESULT: bee

// VARIABLE_PATH: any.a
// EVAL_EXPRESSION: (any as First).a
// EVAL_RESULT: ay

// VARIABLE_PATH: any.b
// EVAL_EXPRESSION: (any as Second).b
// EVAL_RESULT: bee

// VARIABLE_PATH: anyReversed.a
// EVAL_EXPRESSION: (anyReversed as First).a
// EVAL_RESULT: ay

// VARIABLE_PATH: anyReversed.b
// EVAL_EXPRESSION: (anyReversed as Second).b
// EVAL_RESULT: bee

// VARIABLE_PATH: viaLocalBase.inherited
// EVAL_EXPRESSION: (viaLocalBase as NamedBase).inherited
// EVAL_RESULT: base
