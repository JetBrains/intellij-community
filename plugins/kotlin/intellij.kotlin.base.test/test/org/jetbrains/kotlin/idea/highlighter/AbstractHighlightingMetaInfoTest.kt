// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighter

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.PsiFileEx
import com.intellij.testFramework.InspectionTestUtil
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.base.test.ensureFilesResolved
import org.jetbrains.kotlin.idea.test.Directives
import org.jetbrains.kotlin.idea.test.KotlinMultiFileLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.kmp.KMPProjectDescriptorTestUtilities
import org.jetbrains.kotlin.idea.test.kmp.KMPTest
import org.jetbrains.kotlin.idea.test.withCustomCompilerOptions
import org.jetbrains.kotlin.idea.test.withImplicitPackagePrefix
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

abstract class AbstractHighlightingMetaInfoTest : KotlinMultiFileLightCodeInsightFixtureTestCase() {
    protected val HIGHLIGHTING_EXTENSION = "highlighting"

    override fun doMultiFileTest(files: List<PsiFile>, globalDirectives: Directives) {
        val expectedHighlighting = dataFile().getExpectedHighlightingFile()
        val psiFile = files.first()

        if (!isFirPlugin && psiFile is KtFile && psiFile.isScript()) {
            updateScriptDependencies(psiFile)
        }

        if (this is KMPTest) {
            KMPProjectDescriptorTestUtilities.validateTest(files, testPlatform)
        }

        val mainFileText = runReadAction { psiFile.text }
        val tools = InTextDirectivesUtils.findLinesWithPrefixesRemoved(mainFileText, AbstractHighlightingTest.TOOL_PREFIX)
        myFixture.enableInspections(*InspectionTestUtil.instantiateTools(tools.toSet()).toTypedArray())

        val implicitPackagePrefix = InTextDirectivesUtils.findLineWithPrefixRemoved(mainFileText, "IMPLICIT_PACKAGE_PREFIX:")

        files.forEach {
            val fileText = runReadAction { it.text }
            if (InTextDirectivesUtils.isDirectiveDefined(fileText, "BATCH_MODE")) {
                it.putUserData(PsiFileEx.BATCH_REFERENCE_PROCESSING, true)
            }
        }

        runInEdtAndWait {
            withCustomCompilerOptions(psiFile.text, project, module) {
                val directory = psiFile.parent!!
                directory.withImplicitPackagePrefix(implicitPackagePrefix) {
                    if (psiFile is KtFile) {
                        ensureFilesResolved(psiFile)
                    }
                    checkHighlighting(psiFile, expectedHighlighting, globalDirectives, project)
                }
            }
        }
    }

    protected open fun updateScriptDependencies(psiFile: KtFile) {}

    protected fun File.getExpectedHighlightingFile(suffix: String = highlightingFileNameSuffix(this)): File {
        return resolveSibling("$name.$suffix")
    }

    protected open fun highlightingFileNameSuffix(ktFilePath: File): String = HIGHLIGHTING_EXTENSION
}