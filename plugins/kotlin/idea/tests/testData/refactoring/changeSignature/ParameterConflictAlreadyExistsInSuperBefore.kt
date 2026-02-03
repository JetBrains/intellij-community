interface A {
    fun m() {}
}
class B3 : A {
    fun <caret>m(i: Int) {}
}
