class Pair(val first: Int, val second: Int) {
    operator fun component1() = first
    operator fun component2() = second
}

fun <T> use(t: T) = t

fun test() {
    val (<info descr="null">~foo</info>, bar) = Pair(1, 2)
    use(<info descr="null">foo</info>)
    use(bar)
    val (x, y) = Pair(1, 2)
}