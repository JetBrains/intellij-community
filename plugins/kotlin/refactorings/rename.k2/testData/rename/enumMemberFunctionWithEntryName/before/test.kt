enum class E {
    A;

    fun /*rename*/alpha(): String = "alpha"
}

fun outside(): String = E.A.alpha()
