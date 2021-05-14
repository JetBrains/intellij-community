package org.jetbrains.uast.test.kotlin.analysis

class KotlinUStringEvaluatorSimpleStringTest : AbstractKotlinUStringEvaluatorTest() {
    fun `test string interpolation`() = doTest(
        """
            fun simpleStringInterpolation() {
                val a = "aaa"
                val b = "ccc"
                return /*<caret>*/ "${'$'}{a}bbb${'$'}b"
            }
        """.trimIndent(),
        "'aaa''bbb''ccc'"
    )
}