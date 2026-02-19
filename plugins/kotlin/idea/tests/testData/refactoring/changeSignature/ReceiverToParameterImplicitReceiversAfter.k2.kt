class C {
    fun foo(string: String) {
        with(1) {
            bar()
        }
    }

    fun Int.bar() {}
}