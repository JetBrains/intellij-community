// CHECK_ERRORS_AFTER
class Child : JavaBase() {
    fun <caret>alpha(x: Int) {}
}

fun outside() = Child().alpha(1)
