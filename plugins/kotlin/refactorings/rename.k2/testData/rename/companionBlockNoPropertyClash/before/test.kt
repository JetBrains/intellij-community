class Clash {
    companion {
        fun /*rename*/alpha(x: Int): Int = x
        val beta: () -> Int = { 1 }
    }
}

fun outside(): Int = Clash.alpha(1)
fun outside1(): Int = Clash.beta()