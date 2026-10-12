// ERROR: This refactoring cannot merge companion objects whose identity is used.
open class <caret>Base {
    companion object
}

class Child : Base() {
    companion object
}

fun distinct() = Base.let { it as Any } !== Child.let { it as Any }
