// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.evaluate

import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

abstract class AbstractCodeFragmentHighlightingTest : KotlinLightCodeInsightFixtureTestCase() {
    open fun doTest(filePath: String) {
        configureByCodeFragment(filePath)
        checkHighlighting(filePath)
    }

    protected open fun doTestWithImport(filePath: String) {
        configureByCodeFragment(filePath)

        project.executeWriteCommand("Imports insertion") {
            val fileText = FileUtil.loadFile(File(filePath), true)
            val file = myFixture.file as KtFile
            InTextDirectivesUtils.findListWithPrefixes(fileText, "// IMPORT: ").forEach {
                doImport(file, it)
            }
        }

        checkHighlighting(filePath)
    }

    protected open fun doImport(file: KtFile, importName: String) {}

    protected open fun checkHighlighting(filePath: String) {
        myFixture.checkHighlighting(true, false, false)
    }

    protected abstract fun configureByCodeFragment(filePath: String)
}