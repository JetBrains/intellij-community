package test

class Integer(s: String?) {
    companion object {
        fun valueOf(value: String?): Integer {
            return Integer(value)
        }
    }
}

internal object Test {
    fun test() {
        Integer.Companion.valueOf("1")
        Integer.Companion.valueOf("1")
        "1".toInt()
    }
}
