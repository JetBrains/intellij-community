fun main() {
    val set = setOf("alfa", "bravo")
    val any: Any = setOf("charlie", "delta")
    val keys: Any = mapOf(1 to "one", 2 to "two").keys
    //Breakpoint!
    println(set)
}

// VARIABLE_PATH: set.0
// EVAL_EXPRESSION: set.elementAt(0)
// VARIABLE_PATH: set.1
// EVAL_EXPRESSION: set.elementAt(1)
// VARIABLE_PATH: any.0
// EVAL_EXPRESSION: (any as LinkedHashSet<*>).elementAt(0)
// VARIABLE_PATH: keys.0
// EVAL_EXPRESSION: (keys as java.util.LinkedHashMap<*, *>.LinkedKeySet).elementAt(0)
