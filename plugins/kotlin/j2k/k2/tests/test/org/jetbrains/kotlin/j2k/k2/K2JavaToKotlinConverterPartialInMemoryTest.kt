// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k.k2

import org.jetbrains.kotlin.j2k.ConverterSettings

class K2JavaToKotlinConverterPartialInMemoryTest : AbstractK2JavaToKotlinConverterPartialTest() {
    fun testClassSelectionKeepsOriginalJavaFileUnchanged() {
        val javaTextWithCaret =
            """
            class <caret>C {
                String f;
            }
            """.trimIndent()
        val result = convertJavaFileToKotlinPartially(
            javaTextWithCaret,
            ConverterSettings.defaultSettings,
        )

        assertEquals(javaTextWithCaret.replace("<caret>", ""), result.javaTextAfterConversion)
        assertEquals(
            """
            internal class C {
                var f: String? = TODO()
            }
            """.trimIndent(),
            result.kotlinText,
        )
    }

    fun testPreprocessorKeepsOriginalJavaFileUnchanged() {
        val javaTextWithCaret =
            """
            class Test {
                void <caret>foo() {}
                void bar() {}
            }
            """.trimIndent()
        val result = convertJavaFileToKotlinPartiallyWithTestPreprocessor(
            javaTextWithCaret,
            ConverterSettings.defaultSettings,
        )

        assertEquals(javaTextWithCaret.replace("<caret>", ""), result.javaTextAfterConversion)
        assertTrue(result.javaTextAfterConversion.contains("foo"))
        assertFalse(result.javaTextAfterConversion.contains("prebar"))
        assertTrue(result.kotlinText.contains("fun prebar() {}"))
        assertTrue(result.kotlinText.contains("TODO()"))
    }
}
