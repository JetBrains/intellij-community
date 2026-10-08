open class <caret>Base {
    open fun answer() = 42
}

open class Middle : Base()

class Leaf : Middle() {
    override fun answer() = 0
}
