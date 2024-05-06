fun foo(i: Int, j: Int): String {
    when (i) {
        0 -> return when (j) {
            1 -> "0, 1"
            else -> "0, x"
        }

        1 -> return "1, x"
        else -> return "x, x"
    }
}
