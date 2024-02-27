enum class E(protected val p: Int) {
    A(1) {
        override fun bar() {
            foo(this.p)
        }
    },

    B(2) {
        override fun bar() {
        }
    };

    fun foo(p: Int) {}

    abstract fun bar()
}
