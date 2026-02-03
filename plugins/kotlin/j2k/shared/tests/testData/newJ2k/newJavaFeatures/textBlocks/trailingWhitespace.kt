object J {
    @JvmStatic
    fun main(args: Array<String>) {
        // should strip two trailing spaces
        val s = """
                red

                """.trimIndent()

        // should preserve escaped trailing spaces
        // an even number of leading backslashes prevents the escaping
        val s2 = """
                trailing  
                trailing\040
                trailing\ 
                trailing\\040

                """.trimIndent()

        // \s is the same as \040
        val colors = """
                trailing 
                trailing\s
                trailing\ 
                trailing\\s

                """.trimIndent()
    }
}
