class FooBar {
    fun m() {
        f<caret>oo()
    }

    private fun foo() {
        //comment1
        bar()
    }

    private fun bar() {
    }
}