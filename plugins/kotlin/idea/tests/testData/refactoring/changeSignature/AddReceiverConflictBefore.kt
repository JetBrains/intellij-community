class P {
    fun String.foo2() {}
    inner class I {
        fun fo<caret>o2() {}
        fun m() {
            "".foo2()
        }
    }
}