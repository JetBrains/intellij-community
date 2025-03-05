// FLOW: IN

fun n(a: Int) {
    val <caret>s = try {
        a
    } catch (e: Exception) {}
}

fun m() {
    n(1)
}