open class Base

class <caret>Child : Base() {
    // INFO: {"checked": "true"}
    class Nested {
        class DeepNested {
            fun use() = obj() + DeepNested.obj() + Nested.DeepNested.obj() + Child.Nested.DeepNested.obj()

            companion object {
                fun obj() = 1
            }
        }
    }
}
