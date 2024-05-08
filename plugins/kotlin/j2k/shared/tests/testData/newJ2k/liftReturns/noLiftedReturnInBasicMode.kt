// !BASIC_MODE: true
fun foo(i: Int, j: Int): String {
    when (i) {
        0 -> return "1"
        1 -> return "3"
        else -> return "4"
    }
}
