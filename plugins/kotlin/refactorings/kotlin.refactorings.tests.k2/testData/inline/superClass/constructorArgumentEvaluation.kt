fun next(): Int = 1

open class <caret>Base(val value: Int) {
    fun result() = value + value
}

class Child : Base(next())
