interface Iface {
    val name: String
}

interface First { val a: String }
interface Second { val b: String }

fun main() {
    val obj = object : Iface {
        override val name = "anon"
    }
    val two = object : First, Second {
        override val a = "ay"
        override val b = "bee"
        val own = "own"
    }
    val reversed = object : Second, First {
        override val a = "ay"
        override val b = "bee"
    }
    //Breakpoint!
    println(listOf(obj, two, reversed))
}

// VARIABLE_PATH: obj.name
// EVAL_EXPRESSION: obj.name
// EVAL_RESULT: anon

// VARIABLE_PATH: two.a
// EVAL_EXPRESSION: two.a
// EVAL_RESULT: ay

// VARIABLE_PATH: two.b
// EVAL_EXPRESSION: two.b
// EVAL_RESULT: bee

// VARIABLE_PATH: two.own
// EVAL_EXPRESSION: two.own
// EVAL_RESULT: own

// VARIABLE_PATH: reversed.a
// EVAL_EXPRESSION: reversed.a
// EVAL_RESULT: ay

// VARIABLE_PATH: reversed.b
// EVAL_EXPRESSION: reversed.b
// EVAL_RESULT: bee
