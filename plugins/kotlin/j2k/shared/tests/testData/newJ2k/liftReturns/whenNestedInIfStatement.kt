internal class Test {
    fun test(n: Int): String {
        return if (n > 1) {
            when (n) {
                2 -> "2"
                3 -> "3"
                else -> "too high"
            }
        } else {
            "too low"
        }
    }
}
