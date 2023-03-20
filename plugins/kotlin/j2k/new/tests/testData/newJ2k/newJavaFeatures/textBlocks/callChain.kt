object J {
    @JvmStatic
    fun main(args: Array<String>) {
        foo(
            """
                John Q. Smith
                """.trimIndent().substring(8) == "Smith"
        )
    }

    fun foo(b: Boolean) {}
}
