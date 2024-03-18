fun foo(i: Int, j: Int) {
    val a = when (i) {
        0 -> if (j > 0) {
            "1"
        } else {
            "2"
        }

        1 -> "3"
        else -> "4"
    }
}
