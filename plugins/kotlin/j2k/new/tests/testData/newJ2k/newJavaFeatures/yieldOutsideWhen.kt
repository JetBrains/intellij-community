internal class Test {
    fun x() {
        TODO(
            """
            |Cannot convert element: yield is not allowed outside switch expression
            |With text:
            |yield 1;
            """.trimMargin()
        )
    }
}
