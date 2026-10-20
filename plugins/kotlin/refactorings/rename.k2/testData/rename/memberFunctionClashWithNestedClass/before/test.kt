class Outer {
    class Nested(x: Int)

    fun /*rename*/alpha(x: Int) {}
}

fun outside() = Outer().alpha(1)
