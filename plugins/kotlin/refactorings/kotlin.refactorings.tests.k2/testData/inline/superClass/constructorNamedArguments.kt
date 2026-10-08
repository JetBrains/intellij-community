open class <caret>Base(val x: Int, val y: Int) {
    fun result() = x - y
}

class Child : Base(y = 2, x = 5)
