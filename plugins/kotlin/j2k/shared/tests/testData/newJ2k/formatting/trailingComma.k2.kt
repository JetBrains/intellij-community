internal object J {
    @JvmStatic
    fun main(args: Array<String>) {
        // normal case: trailing comma is preserved
        val i = intArrayOf(
            1,
            2,
        )

        // all on one line: trailing comma is removed by Kotlin formatter
        val j = intArrayOf(1, 2)

        // weird case with empty array: comma is removed (this syntax is invalid in Kotlin)
        val k = arrayOf<IntArray?>(intArrayOf(), intArrayOf())
    }
}

internal enum class E {
    A, B,
}

internal enum class E2 {
    A, B,
}

internal enum class E3 {
    A,
    B,
}

internal enum class E4 {
    A,
    B,
}

internal enum class E5 {
    A,
    B,
}

internal enum class E6 {
    A,
    B,
    ;

    fun foo() {}
}

internal enum class E7 {
    A,
    B,
    ;

    fun foo() {}
}
