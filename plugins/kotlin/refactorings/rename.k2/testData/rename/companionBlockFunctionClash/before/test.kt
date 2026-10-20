class Clash {
    companion {
        fun /*rename*/alpha(x: Int): Int = x
        fun beta(x: Int): Int = x + 1
    }
}

fun outside(): Int = Clash.alpha(1)
