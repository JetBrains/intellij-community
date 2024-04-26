object J {
    @JvmStatic
    fun main(args: Array<String>) {
        // escaped backslashes are balanced in pairs
        val backslash = """
                \
                \\

                """.trimIndent()

        // \' and ' are the same thing inside a text block (\' is an "unnecessary escape")
        // in Kotlin they are both translated to just '
        val quote = """
                '
                \'
                \'
                \\'

                """.trimIndent()

        // same thing with double quote
        val doubleQuote = """
                ${'"'}
                \${'"'}
                \${'"'}
                \\${'"'}

                """.trimIndent()

        // an even number of leading backslashes prevents the char escaping
        // it is translated to a regular character ('r', 't', etc.)
        val charEscapes = """
                ${'\r'}
                \r
                \${'\r'}
                \\r

                ${'\t'}
                \t
                \${'\t'}
                \\t

                ${'\b'}
                \b
                \${'\b'}
                \\b

                ${'\u000c'}
                \f
                \${'\u000c'}
                \\f

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
