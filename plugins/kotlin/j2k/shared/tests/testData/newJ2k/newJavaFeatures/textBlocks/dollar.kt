object J {
    @JvmStatic
    fun main(args: Array<String>) {
        val s = """
                ${'$'}
                """.trimIndent()
        val s2 = """
                ${'$'}{'${'$'}'}
                """.trimIndent()
        val dollar1 = """
                ${'$'}a
                """.trimIndent()
        val dollar2 = """
                ${'$'}A
                """.trimIndent()
        val dollar3 = """
                ${'$'}{s}
                """.trimIndent()
        val dollar4 = """
                ${'$'}${'$'}
                """.trimIndent()
    }
}
