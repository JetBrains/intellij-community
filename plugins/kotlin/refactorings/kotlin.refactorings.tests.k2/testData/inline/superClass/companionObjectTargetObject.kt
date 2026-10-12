// ERROR: The target cannot contain a companion object.
open class <caret>Base {
    companion object {
        const val ANSWER = 42
    }
}

object Child : Base()
