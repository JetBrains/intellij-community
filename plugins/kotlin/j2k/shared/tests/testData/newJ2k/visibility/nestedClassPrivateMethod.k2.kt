class Foo {
    fun m() {
        Inner().x()
    }

    private class Inner {
        fun x() {
        }
    }
}
