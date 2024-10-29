// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.test

import java.io.File

data class TestFile(val relativePath: String, val text: String, val isMain: Boolean)

internal object ModuleStructureSplitter {
    private const val PLATFORM_PREFIX = "// PLATFORM:"
    private const val FILE_PATH_PREFIX = "// FILE:"
    private const val IS_MAIN = "// MAIN"

    fun splitPerModule(file: File): MutableMap<String, MutableList<TestFile>> {
        val lines = file.readLines()
        return splitPerModule(lines)
    }

    fun splitPerModule(lines: List<String>): MutableMap<String, MutableList<TestFile>> {
        val result = mutableMapOf<String, MutableList<TestFile>>()
        var currentPlatform: String? = null
        var currentFileName: String? = null
        var currentText: String? = null
        var isMainFile = false

        fun dumpFile(currentPlatform: String?, currentFileName: String?, currentText: String?) {
            if (!currentText.isNullOrEmpty() && currentFileName != null && currentPlatform != null) {
                result.getOrPut(currentPlatform) { mutableListOf() }.add(TestFile(currentFileName, currentText, isMainFile))
            }
        }

        lines.forEach { line ->
            if (line == IS_MAIN) {
                isMainFile = true
            } else if (line.startsWith(PLATFORM_PREFIX, true)) {
                dumpFile(currentPlatform, currentFileName, currentText)
                currentText = ""
                currentFileName = null
                currentPlatform = line.substring(PLATFORM_PREFIX.length).trim()
            } else if (line.startsWith(FILE_PATH_PREFIX)) {
                dumpFile(currentPlatform, currentFileName, currentText)
                isMainFile = false
                currentFileName = line.substringAfter(FILE_PATH_PREFIX).trim()
                currentText = ""
            } else {
                currentText += line + "\n"
            }
        }
        dumpFile(currentPlatform, currentFileName, currentText)
        return result
    }
}