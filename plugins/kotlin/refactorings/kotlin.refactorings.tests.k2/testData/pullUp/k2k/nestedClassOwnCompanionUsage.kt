open class Base

class <caret>Child : Base() {
    // INFO: {"checked": "true"}
    class Nested {
        fun use() = obj() + Nested.obj() + Child.Nested.obj()

        companion object {
            fun obj() = 1
        }
    }
}
