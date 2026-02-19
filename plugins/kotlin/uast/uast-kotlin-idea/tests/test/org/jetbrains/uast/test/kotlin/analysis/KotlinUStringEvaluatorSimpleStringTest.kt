// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

    fun `test param usage with default arguments`() = doTest(
        """
            fun withDefaultParams(a: String = "aaa", b: String = "bbb", c: String) {
              val d = "ddd"
              return /*<caret>*/ a + b + c + d + "eee" 
            }
        """.trimIndent(),
        "'aaa''bbb'NULL'ddd''eee'"
    )
}