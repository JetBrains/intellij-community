enum class E {
    A {
        override fun foo(): Int = 7
    };

    abstract fun <caret>foo(): Int
}