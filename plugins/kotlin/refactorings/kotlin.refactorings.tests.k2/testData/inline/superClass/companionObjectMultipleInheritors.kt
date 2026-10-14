// ERROR: This refactoring cannot copy a companion object into multiple inheritors.
open class <caret>Base {
    companion object {
        var count = 0
    }

    fun next() = ++count
}

class Child : Base()
class Other : Base()
