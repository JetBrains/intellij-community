fun main() {
    // SMART_STEP_INTO_BY_INDEX: 1
    // RESUME: 1
    //Breakpoint!
    foo(("key" to "value"))
}

fun foo(pair: Pair<String, String>) {
    val (key, value) = pair
    println("foo $pair")
}
