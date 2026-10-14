open class <caret>Base(val x: Int) {
    fun result(x: Int) = this.x + x
}

class Child : Base(1)
