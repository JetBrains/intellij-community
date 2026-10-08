open class <caret>Base(x: Int, val y: Int = x) {
    fun result() = y
}

class Child : Base(5)
