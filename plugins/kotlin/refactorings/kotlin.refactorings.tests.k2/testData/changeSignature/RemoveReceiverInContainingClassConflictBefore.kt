class P {
    fun foo2() {}
    inner class I {
        fun String.fo<caret>o2() {}
        fun m() {
            foo2()
        }
    }
}