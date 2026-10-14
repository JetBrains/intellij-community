// ERROR: The target companion object already contains a member named 'answer'.
open class <caret>Base {
    companion object {
        fun answer() = 42
    }
}

class Child : Base() {
    companion object {
        fun answer() = -1
    }
}
