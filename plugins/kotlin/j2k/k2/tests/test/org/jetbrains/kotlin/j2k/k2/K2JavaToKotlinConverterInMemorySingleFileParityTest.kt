// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k.k2

import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import org.jetbrains.kotlin.j2k.ConverterSettings
import java.io.File

class K2JavaToKotlinConverterInMemorySingleFileParityTest : AbstractK2JavaToKotlinConverterSingleFileTest() {
    private fun testDataPath(relativePath: String): String = File(KotlinRoot.DIR, "j2k/shared/tests/testData/$relativePath").path

    fun testOverrideWithHigherVisibility() {
        doTest(testDataPath("newJ2k/function/overrideWithHigherVisibility.java"))
    }

    fun testSeveralInheritors() {
        doTest(testDataPath("newJ2k/protected/severalInheritors.java"))
    }

    fun testUsages() {
        doTest(testDataPath("newJ2k/protected/usages.java"))
    }

    fun testRawGenericMethod() {
        val result = fileToKotlin(
            """
            package demo;

            class TestT {
              <T> void getT() { }
            }

            class U {
              void main() {
                TestT t = new TestT();
                t.<String>getT();
                t.<Integer>getT();
                t.getT();
              }
            }
            """.trimIndent(),
            ConverterSettings.defaultSettings,
        )

        assertEquals(
            """
            package demo

            internal class TestT {
                fun <T> getT() {}
            }

            internal class U {
                fun main() {
                    val t = TestT()
                    t.getT<String?>()
                    t.getT<Int?>()
                    t.getT<Any?>()
                }
            }
            """.trimIndent(),
            result,
        )
    }

    fun testPreprocessorKeepsOriginalJavaFileUnchanged() {
        val javaText =
            """
            class Test {
                void foo() {}
            }
            """.trimIndent()
        val result = convertJavaFileToKotlinWithTestPreprocessor(
            javaText,
            ConverterSettings.defaultSettings,
        )

        assertEquals(javaText, result.javaTextAfterConversion)
        assertTrue(result.javaTextAfterConversion.contains("foo"))
        assertFalse(result.javaTextAfterConversion.contains("prebar"))
        assertTrue(result.kotlinText.contains("fun prebar()"))
        assertNotNull(result.externalCodeProcessing)
    }
}
