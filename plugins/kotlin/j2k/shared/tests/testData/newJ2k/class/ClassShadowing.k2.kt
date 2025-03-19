package test

class Integer(s: String?) {
    companion object {
        fun valueOf(value: String?): Integer {
            return test.Integer(value)
        }
    }
}

internal object Test {
    fun test() {
        test.Integer.Companion.valueOf("1")
        test.Integer.Companion.valueOf("1")
        "1".toInt()
    }
}
