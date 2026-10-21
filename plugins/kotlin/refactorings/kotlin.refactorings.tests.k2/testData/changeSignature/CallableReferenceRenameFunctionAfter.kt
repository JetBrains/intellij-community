fun renamed(n: Int): Int = n

fun test() {
    val f: (Int) -> Int = ::renamed
    f(1)
}