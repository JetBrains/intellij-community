// ERROR: Cannot access 'val p: Int': it is private in 'E'.
enum class E(private val p: Int) {
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
