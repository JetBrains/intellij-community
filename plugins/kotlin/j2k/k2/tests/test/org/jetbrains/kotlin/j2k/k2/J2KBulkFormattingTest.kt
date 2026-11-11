// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.j2k.k2

import com.intellij.testFramework.LoggedErrorProcessor
import org.jetbrains.kotlin.j2k.ConverterSettings

/**
 * The formatter rewrites the whole document when it has more than 3000 whitespace changes.
 * The trailing comma post-format processor then edits the PSI.
 */
class J2KBulkFormattingTest : AbstractK2JavaToKotlinConverterSingleFileTest() {
    fun testTrailingCommaAfterBulkWhitespaceChanges() {
        val methods = (0 until 1500).joinToString("\n") { "static int m$it(int l) { return l; }" }
        val java = "public class C {\nstatic int[] A = new int[] {\n1,\n2,\n};\n$methods\n}"
        val loggedErrors = mutableListOf<String>()
        val processor = object : LoggedErrorProcessor() {
            override fun processError(category: String, message: String, details: Array<String>, t: Throwable?): Set<Action> {
                loggedErrors += message
                return Action.NONE
            }
        }
        lateinit var kotlin: String
        LoggedErrorProcessor.executeWith<RuntimeException>(processor) {
            kotlin = fileToKotlin(java, ConverterSettings.defaultSettings)
        }
        assertEmpty(loggedErrors)
        assertTrue(kotlin, kotlin.contains("var A: IntArray = intArrayOf(\n        1,\n        2,\n    )\n"))
    }
}
