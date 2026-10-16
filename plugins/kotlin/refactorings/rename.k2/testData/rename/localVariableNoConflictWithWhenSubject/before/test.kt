fun conflict1(p: Int) {
    val /*rename*/x = 0
    when (val y = p + 1) {
        0 -> {}
        else -> println(y)
    }
    for (z in 0..p) {
        println(z)
    }
}
