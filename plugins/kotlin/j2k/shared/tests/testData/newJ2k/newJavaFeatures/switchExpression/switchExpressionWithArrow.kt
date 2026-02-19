package test

object J {
    fun foo(x: Int): String {
        return when (x) {
            0 -> "zero"
            1 -> "one"
            2 -> "two"
            else -> "many"
        }
    }
}
