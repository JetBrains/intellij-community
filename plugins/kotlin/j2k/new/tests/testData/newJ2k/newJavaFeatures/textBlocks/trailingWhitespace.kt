object J {
    @JvmStatic
    fun main(args: Array<String>) {
        // should strip two trailing spaces
        val s = """
    red

    """.trimIndent()

        // should preserve two escaped trailing spaces
        val s2 = """
    trailing  
    white space

    """.trimIndent()
        // \s is the same as \040
        val colors = """
    red   
    green 
    blue  

    """.trimIndent()
    }
}
