class Test(
    val x: Int,
    val y: String,
) {
    @JvmOverloads
    constructor(
        x: Long = 42L,
        y: String = "42",
    ): this(x.toInt(), y) {
        if (x > 0) {
            println(x)
        }
    }

    @JvmOverloads
    fun foo(p1: Double = 3.14, p2: String = "pi") {
        if (x > p1.toInt()) {
            println(p1)
        }
        println(p2)
    }

    @JvmOverloads
    fun bar(p1: Double = 25.1, p2: String = "K2"): String = buildString {
        append(p1)
        append(" | ")
        append(p2)
    }
}