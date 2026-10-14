open class <caret>Base {
    open fun answer() = 40
}
class Child : Base() {
    override fun answer() = super.answer() + 2
}
