package test

import java.io.StringReader

//FunctionBreakpoint!
fun List<Int>.process(map: Map<String, String>) {
    // RESUME: 1
    println(this)
    println(map)
}

fun main() {
    listOf(1, 2, 3).process(mapOf())

    // ADDITIONAL_BREAKPOINT: ReadWrite.kt / public fun Reader.readText() / fun
    // STEP_OVER: 1
    val reader = StringReader("test")
    reader.readText()
}
