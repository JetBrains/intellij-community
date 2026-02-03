// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.test.kotlin.analysis

import org.jetbrains.uast.analysis.UNeDfaConfiguration
import org.jetbrains.uast.analysis.UStringBuilderEvaluator

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
        "'a''b''c''e'",
        configuration = {
            UNeDfaConfiguration(
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
        "'a''b''c''d''e''f''g''h'",
        configuration = {
            UNeDfaConfiguration(
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
        "'1''a'{|'c'}'d'",
        configuration = {
            UNeDfaConfiguration(
                builderEvaluators = listOf(UStringBuilderEvaluator)
            )
        }
    )

    fun `test StringBuilder with let`() = doTest(
        """
            fun fnLet(): String {
                return /*<caret>*/ StringBuilder().let {
                    it.append("a")
                    it.append("b")
                    it.append("c").append("d")
                }.toString()
            }
        """.trimIndent(),
        "'a''b''c''d'",
        configuration = {
            UNeDfaConfiguration(
                builderEvaluators = listOf(UStringBuilderEvaluator)
            )
        }
    )

    fun `test StringBuilder with run`() = doTest(
        """
            fun fnRun(): String {
                return /*<caret>*/ StringBuilder().run {
                    append("a")
                    this.append("b")
                    append("c").append("d")
                }.toString()
            }
        """.trimIndent(),
        "'a''b''c''d'",
        configuration = {
            UNeDfaConfiguration(
                builderEvaluators = listOf(UStringBuilderEvaluator)
            )
        }
    )

    fun `test StringBuilder with also`() = doTest(
        """
            fun fnAlso(): String {
                return /*<caret>*/ StringBuilder().also {
                    it.append("a")
                    it.append("b")
                    val sb = it.append("-")
                    sb.append("c").append("d")
                }.toString()
            }
        """.trimIndent(),
        "'a''b''-''c''d'",
        configuration = {
            UNeDfaConfiguration(
                builderEvaluators = listOf(UStringBuilderEvaluator)
            )
        }
    )

    fun `test StringBuilder with apply`() = doTest(
        """
            fun fnApply(): String {
                return /*<caret>*/ StringBuilder().apply {
                    append("a")
                    this.append("b")
                    val sb = append("-")
                    sb.append("c").append("d")
                }.toString()
            }
        """.trimIndent(),
        "'a''b''-''c''d'",
        configuration = {
            UNeDfaConfiguration(
                builderEvaluators = listOf(UStringBuilderEvaluator)
            )
        }
    )

    fun `test StringBuilder with let assigned to variable`() = doTest(
        """
            fun fnLetWithVar(): String {
                val sb = StringBuilder().let { 
                    it.append("a")
                    it.append("b")
                    it.append("c").append("d")
                }
                sb.append("e")
                return /*<caret>*/ sb.append("f").toString()
            }
        """.trimIndent(),
        "'a''b''c''d''e''f'",
        configuration = {
            UNeDfaConfiguration(
                builderEvaluators = listOf(UStringBuilderEvaluator)
            )
        }
    )

    fun `test StringBuilder with run assigned to variable`() = doTest(
        """
            fun fnRunWithVar(): String {
                val sb = StringBuilder().run { 
                    append("a")
                    append("b")
                    append("c").append("d")
                }
                sb.append("e")
                return /*<caret>*/ sb.append("f").toString()
            }
        """.trimIndent(),
        "'a''b''c''d''e''f'",
        configuration = {
            UNeDfaConfiguration(
                builderEvaluators = listOf(UStringBuilderEvaluator)
            )
        }
    )

    fun `test StringBuilder with run and let`() = doTest(
        """
            fun fnLet(): String {
                return /*<caret>*/ StringBuilder().let {
                    it.append("a")
                    it.append("b")
                    it.run {
                      append("c")
                      append("d")
                    }
                }.toString()
            }
        """.trimIndent(),
        "'a''b''c''d'",
        configuration = {
            UNeDfaConfiguration(
                builderEvaluators = listOf(UStringBuilderEvaluator)
            )
        }
    )

    fun `test StringBuilder with let and also with shadowing`() = doTest(
        """
            fun fnLetAndAlso(): String {
                return /*<caret>*/ StringBuilder().let {
                    it.append("a")
                    it.append("b")
                    it.also {
                      it.append("c")
                      it.append("d")
                    }
                }.toString()
            }
        """.trimIndent(),
        "'a''b''c''d'",
        configuration = {
            UNeDfaConfiguration(
                builderEvaluators = listOf(UStringBuilderEvaluator)
            )
        }
    )

    fun `test StringBuilder with let and also with changing in lower scope`() = doTest(
        """
            fun fnLetAndAlso(): String {
                return /*<caret>*/ StringBuilder().let {
                    it.append("a")
                    it.append("b")
                    it.append("c").also { it1 ->
                      it1.append("d")
                      it.append("e")
                      it1.append("f")
                    }
                }.toString()
            }
        """.trimIndent(),
        "'a''b''c''d''e''f'",
        configuration = {
            UNeDfaConfiguration(
                builderEvaluators = listOf(UStringBuilderEvaluator)
            )
        }
    )

    fun `test StringBuilder with apply and variable`() = doTest(
        """
            fun fnRunWithVar(): String {
                val sb = StringBuilder().apply { 
                    append("a")
                    append("b")
                    append("c").append("d")
                }
                sb.append("e")
                return /*<caret>*/ sb.append("f").toString()
            }
        """.trimIndent(),
        "'a''b''c''d''e''f'",
        configuration = {
            UNeDfaConfiguration(
                builderEvaluators = listOf(UStringBuilderEvaluator)
            )
        }
    )

    fun `test StringBuilder with also and variable`() = doTest(
        """
            fun fnRunWithVar(): String {
                val sb = StringBuilder().also { 
                    it.append("a")
                    it.append("b")
                    it.append("c").append("d")
                }
                sb.append("e")
                return /*<caret>*/ sb.append("f").toString()
            }
        """.trimIndent(),
        "'a''b''c''d''e''f'",
        configuration = {
            UNeDfaConfiguration(
                builderEvaluators = listOf(UStringBuilderEvaluator)
            )
        }
    )

    fun `test StringBuilder with also and variable reassignment`() = doTest(
        """
            fun fnRunWithVar(): String {
                var sb = StringBuilder()
                sb = StringBuilder().also { 
                    it.append("a")
                    it.append("b")
                    it.append("c").append("d")
                }
                sb.append("e")
                return /*<caret>*/ sb.append("f").toString()
            }
        """.trimIndent(),
        "'a''b''c''d''e''f'",
        configuration = {
            UNeDfaConfiguration(
                builderEvaluators = listOf(UStringBuilderEvaluator)
            )
        }
    )

    fun `test StringBuilder with run and also and variable`() = doTest(
        """
            fun fnRunWithVar(): String {
                val sb = StringBuilder().run { 
                    append("a")
                    append("b")
                    also {
                        it.append("c")
                        it.append("d")
                    }
                }
                sb.append("e")
                return /*<caret>*/ sb.append("f").toString()
            }
        """.trimIndent(),
        "'a''b''c''d''e''f'",
        configuration = {
            UNeDfaConfiguration(
                builderEvaluators = listOf(UStringBuilderEvaluator)
            )
        }
    )

    fun `test StringBuilder with let and apply and variable`() = doTest(
        """
            fun fnLetWithApplyAndVar(): String {
                val sb = StringBuilder().let { 
                    it.append("a")
                    it.append("b")
                    it.apply {
                        append("c")
                        append("d")
                    }
                }
                sb.append("e")
                return /*<caret>*/ sb.append("f").toString()
            }
        """.trimIndent(),
        "'a''b''c''d''e''f'",
        configuration = {
            UNeDfaConfiguration(
                builderEvaluators = listOf(UStringBuilderEvaluator)
            )
        }
    )

    fun `test StringBuilder with let and let and variable`() = doTest(
        """
            fun fnLetWithLetAndVar(): String {
                val sb = StringBuilder().let { 
                    it.append("a")
                    it.append("b")
                    it.let {
                        it.append("c")
                        it.append("d")
                    }
                }
                sb.append("e")
                return /*<caret>*/ sb.append("f").toString()
            }
        """.trimIndent(),
        "'a''b''c''d''e''f'",
        configuration = {
            UNeDfaConfiguration(
                builderEvaluators = listOf(UStringBuilderEvaluator)
            )
        }
    )

    fun `test StringBuilder with apply with apply and variable`() = doTest(
        """
            fun fnApplyWithApplyAndVar(): String {
                val sb = StringBuilder().apply { 
                    append("a")
                    append("b")
                    apply {
                        append("c")
                        append("d")
                    }
                }
                sb.append("e")
                return /*<caret>*/ sb.append("f").toString()
            }
        """.trimIndent(),
        "'a''b''c''d''e''f'",
        configuration = {
            UNeDfaConfiguration(
                builderEvaluators = listOf(UStringBuilderEvaluator)
            )
        }
    )
}