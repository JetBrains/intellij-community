class A {
    private val s1 = """
            ${'\u0001'}
            """.trimIndent()
    private val s2 = """
            \1
            """.trimIndent()
    private val s3 = """
            \${'\u0001'}
            """.trimIndent()
    private val s4 = """
            \\1
            """.trimIndent()
    private val s5 = """
            \\${'\u0001'}
            """.trimIndent()
    private val s6 = """
            ${'\u0001'}${'\u0001'}
            """.trimIndent()
}
