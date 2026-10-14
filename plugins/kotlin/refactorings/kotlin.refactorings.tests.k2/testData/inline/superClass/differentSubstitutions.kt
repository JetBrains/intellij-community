open class <caret>Base<T> {
    fun identity(value: T): T = value
}
class Child : Base<String>()
class Other : Base<Int>()
