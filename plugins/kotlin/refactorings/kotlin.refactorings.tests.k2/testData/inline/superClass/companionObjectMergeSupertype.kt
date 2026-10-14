// ERROR: This refactoring cannot merge a companion object with supertypes.
interface Factory {
    fun answer(): Int
}

open class <caret>Base {
    companion object : Factory {
        override fun answer() = 42
    }
}

class Child : Base() {
    companion object
}
