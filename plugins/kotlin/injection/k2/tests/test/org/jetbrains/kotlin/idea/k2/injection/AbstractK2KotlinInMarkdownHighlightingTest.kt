// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.injection

import org.jetbrains.kotlin.idea.highlighter.ALLOW_ERRORS
import org.jetbrains.kotlin.idea.highlighter.checkHighlighting
import org.jetbrains.kotlin.idea.test.Directives
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import java.nio.file.Paths

abstract class AbstractK2KotlinInMarkdownHighlightingTest : KotlinLightCodeInsightFixtureTestCase() {
    fun doTest(testdataPath: String) {
        val testDataFile = Paths.get(testdataPath).toFile()
        require(testDataFile.extension == "md") { "Only markdown files are supported" }
        myFixture.configureByFile(testDataFile.name)
        checkHighlighting(
            myFixture.file,
            expectedHighlightingFile = testDataFile.resolveSibling(testDataFile.name + ".highlighting"),
            Directives().apply { put(ALLOW_ERRORS, "")},
            project
        )
    }
}