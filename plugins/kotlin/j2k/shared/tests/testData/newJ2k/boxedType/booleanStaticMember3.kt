class J {
    private val b = true

    init {
        foo(java.lang.Boolean.TRUE)
        if (b) {
            println("true")
        }
    }

    private fun <T> foo(value: T) {
    }
}
