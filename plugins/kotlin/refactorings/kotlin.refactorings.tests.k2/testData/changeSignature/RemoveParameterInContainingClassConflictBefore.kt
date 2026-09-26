class P {
    fun foo2() {}
    inner class I {
        fun fo<caret>o2(s: String) {}
        fun m() {
            foo2()
        }
    }
}