open class <caret>Base(val value: Int) {
    fun result() = value + value
}

class Child(input: Int) : Base(input)
