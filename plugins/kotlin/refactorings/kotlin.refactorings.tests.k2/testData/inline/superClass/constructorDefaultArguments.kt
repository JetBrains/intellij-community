open class <caret>Base(val x: Int = 4, val text: String = "base") {
    fun result() = text + x
}

class Child : Base()
