open class <caret>Base(val number: Long, val value: Any) {
    fun number() = number
    fun value() = value
}

class Child : Base(1, "text")
