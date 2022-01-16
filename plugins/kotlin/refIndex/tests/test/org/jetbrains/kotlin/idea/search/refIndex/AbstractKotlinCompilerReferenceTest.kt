// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.search.refIndex

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.testFramework.assertEqualsToFile
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
import kotlin.io.path.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.reader

abstract class AbstractKotlinCompilerReferenceTest : KotlinCompilerReferenceTestBase() {
    override fun setUp() {
        super.setUp()
        installCompiler()
    }

    protected fun doTest(testDataFilePath: String) {
        myFixture.testDataPath = testDataFilePath

        val configurationPath = Path(testDataFilePath, "testConfig.json")
        val config: JsonObject = JsonParser.parseReader(configurationPath.reader()).asJsonObject

        val mainFile = config[MAIN_FILE]?.asString ?: error("Main file not found")
        val shouldBeUsage = config[SHOULD_BE_USAGE]?.asJsonArray?.map { it.asString }?.toSet().orEmpty()

        val allFiles = listOf(mainFile) + Path(testDataFilePath).listDirectoryEntries().map { it.name }.minus(mainFile)
        myFixture.configureByFiles(*allFiles.toTypedArray())
        rebuildProject()

        val actualUsages = getReferentFilesForElementUnderCaret()
        assertEqualsToFile(
            "",
            configurationPath.toFile(),
            createActualText(mainFile, actualUsages, shouldBeUsage)
        )
    }

    companion object {
        private const val USAGES = "usages"
        private const val SHOULD_BE_USAGE = "shouldBeUsage"
        private const val MAIN_FILE = "mainFile"

        private fun createActualText(mainFile: String, actualUsages: Set<String>?, shouldBeUsage: Set<String>): String {
            val mainFileText = createPair(MAIN_FILE, "\"$mainFile\"")
            val actualUsagesText = actualUsages?.ifNotEmpty { createPair(USAGES, createArray(this)) }
            val shouldBeFixedText = shouldBeUsage.minus(actualUsages.orEmpty()).ifNotEmpty {
                createPair(SHOULD_BE_USAGE, createArray(this))
            }

            return listOfNotNull(mainFileText, actualUsagesText, shouldBeFixedText).joinToString(
                prefix = "{\n    ",
                separator = ",\n    ",
                postfix = "\n}"
            )
        }

        private fun createArray(values: Set<String>): String = values.sorted().joinToString(
            prefix = "[\n        \"",
            separator = "\",\n        \"",
            postfix = "\"\n    ]"
        )

        private fun createPair(name: String, value: String) = "\"$name\": $value"
    }
}