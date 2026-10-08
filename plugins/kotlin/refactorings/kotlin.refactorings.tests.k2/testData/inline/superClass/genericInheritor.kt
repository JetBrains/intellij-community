open class <caret>Base<T> {
    fun box(value: T): List<T> = listOf(value)
}

class Child<E> : Base<E>()
