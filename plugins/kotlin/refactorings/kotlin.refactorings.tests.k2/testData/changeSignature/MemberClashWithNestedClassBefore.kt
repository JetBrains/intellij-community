class Outer {
    class Nested(x: Int)

    fun <caret>alpha(x: Int) {}
}

fun outside() = Outer().alpha(1)
