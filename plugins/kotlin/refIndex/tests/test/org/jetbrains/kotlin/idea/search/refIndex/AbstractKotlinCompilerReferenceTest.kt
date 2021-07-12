// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.search.refIndex

import com.google.gson.JsonObject
import com.google.gson.JsonParser
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

        val usages = config["usages"].asJsonArray.map { it.asString }
        val mainFile = config["mainFile"]?.asString ?: usages.first()

        val allFiles = listOf(mainFile) + Path(testDataFilePath).listDirectoryEntries().map { it.name }.minus(mainFile)

        myFixture.configureByFiles(*allFiles.toTypedArray())
        rebuildProject()
        assertEquals(usages.toSet(), getReferentFilesForElementUnderCaret())
    }
}