internal class Test {
    fun test(n: Int): String {
        return if (n == 1) {
            // returns one
            "one"
        } else if (n == 2) {
            "two"
        } else {
            // returns three
            "three"
        }
    }
}
