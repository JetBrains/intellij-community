class Pair(val first: Int, val second: Int) {
    operator fun component1() = first
    operator fun component2() = second
}

fun <T> use(t: T) = t

fun <T, R> T.let(block: (T) -> R): R {
    return block(this)
}

fun test() {
    Pair(1, 2).let { (<info descr="null">~foo</info>, bar) ->
        use(<info descr="null">foo</info>)
        use(bar)
    }
    val (x, y) = Pair(1, 2)
}
