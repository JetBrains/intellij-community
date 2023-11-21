package stepIntoLibWithSources

import java.io.StringReader

fun main() {
    //Breakpoint!
    val text = StringReader("OK").readText()
}
// the order is readText, StringReader() due to stepping filters prioritisation
// SMART_STEP_INTO_BY_INDEX: 1
// IGNORE_K2