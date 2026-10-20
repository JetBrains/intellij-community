class Child : JavaBase() {
    fun /*rename*/alpha(x: Int) {}
}

fun outside() = Child().alpha(1)
