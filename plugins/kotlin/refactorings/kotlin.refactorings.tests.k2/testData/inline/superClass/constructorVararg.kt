// ERROR: This refactoring cannot preserve the initialization of this constructor.
open class <caret>Base(vararg values: Int) {
    val result = values.sum()
}

class Child : Base(1, 2)
