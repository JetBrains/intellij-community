interface <caret>Base {
    fun answer(): Int
}
class Child : Base {
    override fun answer() = 42
}
