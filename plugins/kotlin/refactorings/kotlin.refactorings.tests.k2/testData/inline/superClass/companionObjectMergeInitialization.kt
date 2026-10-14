// ERROR: This refactoring cannot preserve companion object initialization when it merges the companion objects.
val events = mutableListOf<String>()

open class <caret>Base {
    companion object {
        init {
            events.add("base")
        }
    }
}

class Child : Base() {
    companion object {
        init {
            events.add("child")
        }
    }
}
