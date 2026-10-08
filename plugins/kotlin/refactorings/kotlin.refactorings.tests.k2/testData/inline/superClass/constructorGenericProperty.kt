open class <caret>Base<T>(val value: T) {
    fun result(): T = value
}

class Child(input: String) : Base<String>(input)
