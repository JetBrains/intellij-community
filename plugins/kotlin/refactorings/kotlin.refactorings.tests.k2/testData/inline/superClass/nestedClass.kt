open class <caret>Base {
    class Nested {
        fun answer() = 42
    }

    object Shared
}

class Child : Base()

fun use() = Base.Nested().answer()
fun shared() = Base.Shared
