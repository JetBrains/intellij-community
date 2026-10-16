fun conflict1(p: Int) {
    val y = 0
    when (val y = p + 1) {
        0 -> {}
        else -> println(y)
    }
    for (z in 0..p) {
        println(z)
    }
}
