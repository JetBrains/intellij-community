class Test {
    var s1: String = """
        asdf
        asdfasdf
        """.trimIndent()
    var s2: String = """
        asdf
        asdfasdf

        """.trimIndent()
    var s3: String = """
        asdf
        nadfadsf
        asdfasdf
        """.trimIndent()
    var s4: String = """
        asdf
        nadfadsf
        asdfasdf

        """.trimIndent()
    var trailingNewlinesOnly: String = "asdf" + "\n"
    var trailingNewlinesOnly2: String = "asdf\n" + "\n"
}
