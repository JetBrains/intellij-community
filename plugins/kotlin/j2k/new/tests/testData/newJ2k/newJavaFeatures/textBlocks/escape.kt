object J {
    @JvmStatic
    fun main(args: Array<String>) {
        val escapes = """
                \
                '
                ${'"'}
                ${'\r'}
                ${'\t'}
                ${'\b'}
                ${'\u000c'}

                """.trimIndent()
        val newlines = """
                foo
                bar
                baz

                """.trimIndent()
        val suppressNewlines = """
              This is a single line.
              """.trimIndent()
    }
}
