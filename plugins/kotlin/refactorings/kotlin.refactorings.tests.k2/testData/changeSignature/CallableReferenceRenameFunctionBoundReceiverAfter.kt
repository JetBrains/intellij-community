class A {
    fun renamed(n: Int): Int = n
}

fun test(a: A) {
    val f: (Int) -> Int = a::renamed
    f(1)
}