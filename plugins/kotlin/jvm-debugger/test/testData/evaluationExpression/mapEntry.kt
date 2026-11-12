fun main() {
    val map = mapOf(1 to "one", 2 to "two")
    val any: Any = mapOf(3 to "three", 4 to "four")
    val entry = map.entries.first()
    val anyEntry: Any = map.entries.last()
    val hashEntry: Any = hashMapOf(5 to "five").entries.first()
    //Breakpoint!
    println(map)
}

// VARIABLE_PATH: map.0
// EVAL_EXPRESSION: map.entries.elementAt(0)
// VARIABLE_PATH: map.0.key
// EVAL_EXPRESSION: map.entries.elementAt(0).key
// VARIABLE_PATH: map.1.value
// EVAL_EXPRESSION: map.entries.elementAt(1).value
// VARIABLE_PATH: any.0
// EVAL_EXPRESSION: (any as LinkedHashMap<*, *>).entries.elementAt(0)
// VARIABLE_PATH: entry.key
// EVAL_EXPRESSION: entry.key
// VARIABLE_PATH: entry.value
// EVAL_EXPRESSION: entry.value
// VARIABLE_PATH: anyEntry.key
// EVAL_EXPRESSION: (anyEntry as Map.Entry<*, *>).key
// VARIABLE_PATH: hashEntry.value
// EVAL_EXPRESSION: (hashEntry as Map.Entry<*, *>).value
