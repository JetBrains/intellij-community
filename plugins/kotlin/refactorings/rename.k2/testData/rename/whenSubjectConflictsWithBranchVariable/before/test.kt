fun conflict2(p: Int) {
    when (val /*rename*/y = p + 1) {
        0 -> {
            val v = p + 2
            println(v)
        }
        else -> println(y)
    }
}
