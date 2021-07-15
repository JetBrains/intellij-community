// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.search.refIndex

import com.google.gson.JsonObject
import com.google.gson.JsonParser
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

        val usages = config[USAGES]?.asJsonArray?.map { it.asString }
        val mainFile = config["mainFile"]?.asString ?: usages?.first() ?: error("Main file not found")
        val shouldBeFixed = config[SHOULD_BE_USAGE]?.asJsonArray?.map { it.asString }.orEmpty()
        val usagesSet = usages?.also { it.intersect(shouldBeFixed).ifNotEmpty { error("$this should be omitted in '$USAGES'") } }?.toSet()

        val allFiles = listOf(mainFile) + Path(testDataFilePath).listDirectoryEntries().map { it.name }.minus(mainFile)
        myFixture.configureByFiles(*allFiles.toTypedArray())
        rebuildProject()

        assertEquals(
            usagesSet,
            getReferentFilesForElementUnderCaret()?.also {
                it.intersect(shouldBeFixed).ifNotEmpty { error("$this should be moved from '$SHOULD_BE_USAGE' to '$USAGES") }
            }
        )
    }

    companion object {
        private const val USAGES = "usages"
        private const val SHOULD_BE_USAGE = "shouldBeUsage"
    }
}