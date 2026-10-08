open class <caret>Base {
    open val value = 40
}

class Child : Base() {
    override val value = 42
}
// IGNORE_K2
