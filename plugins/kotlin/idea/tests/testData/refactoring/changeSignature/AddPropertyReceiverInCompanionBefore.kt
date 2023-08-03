class A {
    companion object {
        val <caret>p: String
            get() = ""
    }

    fun test() {
        val test = p
    }
}