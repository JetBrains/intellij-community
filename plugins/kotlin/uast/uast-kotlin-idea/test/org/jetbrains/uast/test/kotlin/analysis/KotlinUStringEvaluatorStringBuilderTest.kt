package org.jetbrains.uast.test.kotlin.analysis

import org.jetbrains.uast.analysis.UStringBuilderEvaluator
import org.jetbrains.uast.analysis.UStringEvaluator

class KotlinUStringEvaluatorStringBuilderTest : AbstractKotlinUStringEvaluatorTest() {
    fun `test simple buildString`() = doTest(
        """
            fun simpleDslStringBuilder(): String {
                return /*<caret>*/ buildString {
                    append("a").append("b")
                    append("c")
                    this.append("e")
                }
            }
        """.trimIndent(),
        "'''a''b''c''e'",
        configuration = {
            UStringEvaluator.Configuration(
                builderEvaluators = listOf(UStringBuilderEvaluator)
            )
        }
    )

    fun `test buildString with this update through reference`() = doTest(
        """
            fun simpleDslStringBuilderWithVariable(): String {
                return /*<caret>*/ buildString {
                    append("a")
                    this.append("b")
                    append("c").append("d")
                    val sb = append("e")
                    sb.append("f")
                    append("g")
                    sb.append("h")
                }
            }
        """.trimIndent(),
        "'''a''b''c''d''e''f''g''h'",
        configuration = {
            UStringEvaluator.Configuration(
                builderEvaluators = listOf(UStringBuilderEvaluator)
            )
        }
    )

    fun `test buildString with this update through optional references`() = doTest(
        """
            fun fn(param: Boolean): String {
                return /*<caret>*/ buildString {
                    val sb1 = StringBuilder()
                    val sb = if (param) sb1.append("0") else append("1")
                    append("a")
                    sb1.append("b")
                    sb.append("c")
                    this.append("d")
                }
            }
        """.trimIndent(),
        "'''1''a'{''|'c'}'d'",
        configuration = {
            UStringEvaluator.Configuration(
                builderEvaluators = listOf(UStringBuilderEvaluator)
            )
        }
    )
}