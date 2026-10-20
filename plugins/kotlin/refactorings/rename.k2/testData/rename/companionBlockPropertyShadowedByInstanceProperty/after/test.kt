class Clash {
    companion {
        val p: Int = 0
    }

    val p: Int = 1

    fun use(): Int = Clash.p
}

fun outside(): Int = Clash().p
