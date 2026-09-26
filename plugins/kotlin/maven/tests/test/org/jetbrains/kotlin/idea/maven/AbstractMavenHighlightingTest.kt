// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.maven

import org.jetbrains.kotlin.idea.highlighter.CHECK_SYMBOL_NAMES
import org.jetbrains.kotlin.idea.highlighter.checkHighlighting
import org.jetbrains.kotlin.idea.test.KotlinTestUtils.TestFile
import org.jetbrains.kotlin.idea.test.KotlinTestUtils.parseDirectives
import java.nio.file.Path

abstract class AbstractMavenHighlightingTest(
  mavenVersion: String,
  modelVersion: String,
) : AbstractMavenImportingTest(mavenVersion, modelVersion) {

    override val beforeDirectoryPrefix: String = ""
    override val afterDirectoryPrefix: String = ""

    override fun checkExpected(afterDirectory: Path) {

    }

    override fun doTestAction(mainFile: TestFile) {
        val directives = parseDirectives(mainFile.content).also {
            it.put(CHECK_SYMBOL_NAMES, true.toString())
        }
        val mainPsiFile = codeInsightTestFixture.file
        val expectedHighlightingFileName = mainPsiFile.name + ".highlighting"
        val expectedHighlightingFile = mainPsiFile.virtualFile.toNioPath().parent.resolve(expectedHighlightingFileName)
        checkHighlighting(
            mainPsiFile,
            expectedHighlightingFile.toFile(),
            directives,
            codeInsightTestFixture.project
        )
    }
}
