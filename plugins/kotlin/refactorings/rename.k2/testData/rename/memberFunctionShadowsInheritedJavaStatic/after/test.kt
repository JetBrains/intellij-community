class Child : JavaStaticOwner() {
    fun helper(x: Int) {}

    fun use(): Int = JavaStaticOwner.helper(1)
}

fun outside() = Child().helper(1)
