fun foo(i: Int, j: Int): String {
    val a: Int = return when (i) {
        0 -> {
            if (j > 0) {
                "1"
            } else "2"
        }

        1 -> "2"
        else -> "3"
    }
}
