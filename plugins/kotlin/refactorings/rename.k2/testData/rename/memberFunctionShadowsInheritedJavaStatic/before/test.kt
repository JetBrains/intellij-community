class Child : JavaStaticOwner() {
    fun /*rename*/alpha(x: Int) {}

    fun use(): Int = helper(1)
}

fun outside() = Child().alpha(1)
