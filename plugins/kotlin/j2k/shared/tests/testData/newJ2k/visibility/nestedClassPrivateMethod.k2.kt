import Foo.Inner

class Foo {
    fun m() {
        Inner().x()
    }

    private class Inner {
        fun x() {
        }
    }
}
