// !BASIC_MODE: true
class Test {
    fun testBasicMode(s1: String) {
        if (s1 == null) {
            throw IllegalArgumentException("s should not be null")
        }
    }
}
