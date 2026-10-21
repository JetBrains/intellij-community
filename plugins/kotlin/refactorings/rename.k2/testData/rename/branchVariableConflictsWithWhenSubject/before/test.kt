fun conflict2(p: Int) {
    when (val y = p + 1) {
        0 -> {
            val /*rename*/v = p + 2
            println(v)
        }
        else -> println(y)
    }
}
