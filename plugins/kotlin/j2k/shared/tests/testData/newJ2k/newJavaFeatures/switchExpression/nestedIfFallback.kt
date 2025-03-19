fun foo(i: Int, j: Int): String {
    val a: Int = when (i) {
        0 -> {
            if (j > 0) {
                return "1"
            }
            return "2"
        }

        1 -> return "2"
        else -> return "3"
    }
}
