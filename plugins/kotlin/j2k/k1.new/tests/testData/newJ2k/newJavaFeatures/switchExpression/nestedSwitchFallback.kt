fun foo(i: Int, j: Int) {
    val a = when (i) {
        0 -> {
            when (j) {
                1 -> "0, 1"
                2 -> "0, 2"
            }
            "1, x"
        }

        1 -> "1, x"
        else -> "x, x"
    }
}
