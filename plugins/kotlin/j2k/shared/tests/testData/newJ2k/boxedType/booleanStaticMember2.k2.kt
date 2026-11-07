class J {
    val b: Boolean = true

    init {
        foo<Boolean?>(java.lang.Boolean.TRUE)
        if (b) {
            println("true")
        }
    }

    private fun <T> foo(value: T?) {
    }
}
