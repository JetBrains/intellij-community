// FLOW: IN

fun test(m: Int, n: Int) {
    val <caret>x = when (m) {
        1 -> {
            val p = ""
            1
        }
        2 -> n
        else -> 0
    }
}