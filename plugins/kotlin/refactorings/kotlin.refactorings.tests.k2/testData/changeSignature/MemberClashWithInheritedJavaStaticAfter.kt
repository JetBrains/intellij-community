// CHECK_ERRORS_AFTER
class Child : JavaBase() {
    fun helper(x: Int) {}
}

fun outside() = Child().helper(1)
