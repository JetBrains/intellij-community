// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.rename

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.parentOfTypes
import com.intellij.refactoring.rename.HeadlessRenameProcessor
import com.intellij.refactoring.rename.HeadlessRenameResult
import com.intellij.testFramework.PlatformTestUtil
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.idea.test.Directives
import org.jetbrains.kotlin.idea.test.KotlinBaseTest.TestFile
import org.jetbrains.kotlin.idea.test.KotlinLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.KotlinMultiFileLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.TestFiles
import org.jetbrains.kotlin.idea.test.extractMarkerOffset
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.test.util.invalidateCaches

abstract class AbstractK2HeadlessRenameProcessorTest : KotlinMultiFileLightCodeInsightFixtureTestCase() {

    override fun getDefaultProjectDescriptor(): KotlinLightProjectDescriptor =
        KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()

    override fun tearDown() {
        runAll(
            { project.invalidateCaches() },
            { super.tearDown() },
        )
    }

    override fun doTest(testDataPath: String) {
        IgnoreTests.runTestIfNotDisabledByFileDirective(
            dataFilePath(),
            IgnoreTests.DIRECTIVES.IGNORE_K2,
            test = { super.doTest(testDataPath) }
        )
    }

    @OptIn(KaAllowAnalysisOnEdt::class)
    override fun doMultiFileTest(files: List<PsiFile>, globalDirectives: Directives) = allowAnalysisOnEdt {
        val mainFile = files.first()
        myFixture.configureFromExistingVirtualFile(mainFile.virtualFile)

        val doc = PsiDocumentManager.getInstance(project).getDocument(mainFile)
        val marker = doc?.extractMarkerOffset(project, "<caret>") ?: -1

        val newName = InTextDirectivesUtils.findStringWithPrefixes(mainFile.text, "// NEW_NAME: ")
            ?: InTextDirectivesUtils.findStringWithPrefixes(mainFile.text, "// RENAME: ")
            ?: error("Directive // NEW_NAME: <name> is missing in ${mainFile.name}")

        val target = findTargetElement(mainFile, marker)

        val analysis = PlatformTestUtil.waitForFuture(ApplicationManager.getApplication().executeOnPooledThread<HeadlessRenameResult> {
            runBlockingMaybeCancellable { readAction { HeadlessRenameProcessor.analyze(project, target, newName) } }
        })
        val planned = assertInstanceOf(analysis, HeadlessRenameResult.Planned::class.java)
        val result = planned.plan.apply()
        assertInstanceOf(result, HeadlessRenameResult.Applied::class.java)

        checkResults(files)
    }

    private fun findTargetElement(file: PsiFile, markerOffset: Int): PsiElement {
        val offset = if (markerOffset >= 0) markerOffset else myFixture.caretOffset
        val element = file.findElementAt(offset)?.takeIf { it !is PsiWhiteSpace }
            ?: (if (offset > 0) file.findElementAt(offset - 1) else null)

        return element?.parentOfTypes(KtNamedDeclaration::class, PsiNamedElement::class, withSelf = true)
            ?: error("Target element to rename not found at caret in ${file.name}")
    }

    private fun checkResults(initialFiles: List<PsiFile>) {
        val afterFile = dataFile("${dataFile().name}.after")
        if (!afterFile.exists()) {
            return
        }

        val afterText = afterFile.readText()
        if (afterText.contains("// FILE:")) {
            val afterSubFiles = TestFiles.createTestFiles(
                /* testFileName = */ "single.kt",
                /* expectedText = */ afterText,
                object : TestFiles.TestFileFactoryNoModules<TestFile>() {
                    override fun create(fileName: String, text: String, directives: Directives): TestFile {
                        return TestFile(fileName, text, directives)
                    }
                }
            )

            val candidateDirs = (initialFiles.mapNotNull { it.virtualFile?.parent } +
                    listOfNotNull(myFixture.file?.virtualFile?.parent, myFixture.tempDirFixture.getFile(""))).distinct()

            for (expectedFile in afterSubFiles) {
                val actualVFile = candidateDirs.firstNotNullOfOrNull { it.findFileByRelativePath(expectedFile.name) }
                    ?: initialFiles.firstNotNullOfOrNull { if (it.virtualFile?.name == expectedFile.name) it.virtualFile else null }
                    ?: myFixture.findFileInTempDir(expectedFile.name)
                    ?: myFixture.tempDirFixture.getFile(expectedFile.name)
                    ?: error("Expected file ${expectedFile.name} was not found after rename. Available files: ${candidateDirs.flatMap { it.children.toList() }.map { it.name }}")
                val actualPsiFile = actualVFile.toPsiFile(project)
                    ?: error("PsiFile for ${expectedFile.name} not found")
                assertEquals(
                    "Content mismatch for file ${expectedFile.name}",
                    expectedFile.content.withoutFileDirectives(),
                    actualPsiFile.text.withoutFileDirectives(),
                )
            }
        } else {
            myFixture.checkResultByFile("${dataFile().name}.after")
        }
    }

    private fun String.withoutFileDirectives(): String =
        lines().filterNot { it.startsWith("// FILE:") }.joinToString("\n").trim()
}
