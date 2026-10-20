class Clash {
    companion {
        fun beta(x: Int): Int = x
        val beta: () -> Int = { 1 }
    }
}

fun outside(): Int = Clash.beta(1)
fun outside1(): Int = Clash.beta()