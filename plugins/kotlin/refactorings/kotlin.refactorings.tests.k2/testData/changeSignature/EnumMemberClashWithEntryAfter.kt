// CHECK_ERRORS_AFTER
enum class E {
    A;

    fun A(): String = "alpha"
}

fun outside(): String = E.A.A()
