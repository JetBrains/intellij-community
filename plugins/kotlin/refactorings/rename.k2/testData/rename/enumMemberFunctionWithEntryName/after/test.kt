enum class E {
    A;

    fun A(): String = "A"
}

fun outside(): String = E.A.A()
