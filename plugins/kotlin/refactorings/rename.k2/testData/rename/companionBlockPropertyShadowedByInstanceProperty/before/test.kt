class Clash {
    companion {
        val p: Int = 0
    }

    val /*rename*/q: Int = 1

    fun use(): Int = p
}

fun outside(): Int = Clash().q
