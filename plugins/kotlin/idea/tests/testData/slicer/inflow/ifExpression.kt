// FLOW: IN

fun test(m: Int, n: Int) {
    val <caret>x = if (m > 1) {
        if (m > 2) n else 2
    } else 1
}