open class Parent(val value: Int)

open class <caret>Base(x: Int) : Parent(x) {
    val result = x + 1
}

class Child : Base(4)
