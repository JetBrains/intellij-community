open class <caret>Base(val value: Int)

class Child : Base(1) {
    fun result() = super.value
}
