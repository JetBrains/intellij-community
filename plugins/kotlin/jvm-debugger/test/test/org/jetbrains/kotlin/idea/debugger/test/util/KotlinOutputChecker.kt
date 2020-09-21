/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.debugger.test.util

import com.intellij.debugger.impl.OutputChecker
import com.intellij.idea.IdeaLogger
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.testFramework.assertEqualsToFile
import java.io.File

internal class KotlinOutputChecker(
    private val testDir: String,
    appPath: String,
    outputPath: String,
    private val useIrBackend: Boolean,
    private val expectedOutputFile: File,
) : OutputChecker(appPath, outputPath) {
    companion object {
        @JvmStatic
        private val LOG = Logger.getInstance(KotlinOutputChecker::class.java)

        private const val CONNECT_PREFIX = "Connected to the target VM"
        private const val DISCONNECT_PREFIX = "Disconnected from the target VM"
        private const val RUN_JAVA = "Run Java"

        //ERROR: JDWP Unable to get JNI 1.2 environment, jvm->GetEnv() return code = -2
        private val JDI_BUG_OUTPUT_PATTERN_1 =
            Regex("ERROR:\\s+JDWP\\s+Unable\\s+to\\s+get\\s+JNI\\s+1\\.2\\s+environment,\\s+jvm->GetEnv\\(\\)\\s+return\\s+code\\s+=\\s+-2")

        //JDWP exit error AGENT_ERROR_NO_JNI_ENV(183):  [../../../src/share/back/util.c:820]
        private val JDI_BUG_OUTPUT_PATTERN_2 = Regex("JDWP\\s+exit\\s+error\\s+AGENT_ERROR_NO_JNI_ENV.*]")
    }

    private lateinit var myTestName: String

    override fun init(testName: String) {
        super.init(testName)
        this.myTestName = Character.toLowerCase(testName[0]) + testName.substring(1)
    }

    private fun getExpectedFile(): File {
        if (useIrBackend) {
            val irFile = File(testDir, "$myTestName.ir.out")
            if (irFile.exists()) {
                return irFile
            }
        }

        return File(testDir, "$myTestName.out")
    }

    override fun checkValid(jdk: Sdk, sortClassPath: Boolean) {
        if (IdeaLogger.ourErrorsOccurred != null) {
            throw IdeaLogger.ourErrorsOccurred
        }

        val actualText = preprocessBuffer(buildOutputString())
        assertEqualsToFile("Debugger output", getExpectedFile(), actualText)
    }

    private fun preprocessBuffer(buffer: String): String {
        val lines = buffer.lines().toMutableList()

        val connectedIndex = lines.indexOfFirst { it.startsWith(CONNECT_PREFIX) }
        lines[connectedIndex] = CONNECT_PREFIX

        val runCommandIndex = connectedIndex - 1
        lines[runCommandIndex] = RUN_JAVA

        val disconnectedIndex = lines.indexOfFirst { it.startsWith(DISCONNECT_PREFIX) }
        lines[disconnectedIndex] = DISCONNECT_PREFIX

        return lines
            .map { it.replace("FRAME:(.*):\\d+".toRegex(), "$1:!LINE_NUMBER!") }
            .filter { !(it.matches(JDI_BUG_OUTPUT_PATTERN_1) || it.matches(JDI_BUG_OUTPUT_PATTERN_2)) }
            .joinToString("\n")
    }

    private fun buildOutputString(): String {
        // Call base method with reflection
        val m = OutputChecker::class.java.getDeclaredMethod("buildOutputString")!!
        val isAccessible = m.isAccessible

        try {
            m.isAccessible = true
            return m.invoke(this) as String
        } finally {
            m.isAccessible = isAccessible
        }
    }
}
