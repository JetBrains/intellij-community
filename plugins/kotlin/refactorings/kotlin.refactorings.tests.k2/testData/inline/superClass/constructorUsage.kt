open class <caret>Base {
    fun answer() = 42
}

class Child(val value: Int = 0) : Base()

fun create() = Base()
