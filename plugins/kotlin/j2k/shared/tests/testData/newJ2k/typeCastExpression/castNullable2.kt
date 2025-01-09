class TestCastAndCheckForNull {
    fun foo(): String {
        val s = bar() as String?
        if (s == null) return "null"
        return s
    }

    fun bar(): Any {
        return "abc"
    }
}
