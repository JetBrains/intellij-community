// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.changeSignature

import com.intellij.lang.LanguageRefactoringSupport
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import org.jetbrains.kotlin.idea.refactoring.rename.checkMarkedRenameResult
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.withCustomCompilerOptions
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

abstract class AbstractK2ChangeSignatureRenameTest : KotlinLightCodeInsightFixtureTestCase() {
    override val testDataDirectory: File
        get() = KotlinRoot.DIR.resolve("refactorings/rename.k2/testData/rename/companionConflicts")

    override fun getProjectDescriptor(): LightProjectDescriptor = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()

    fun doTest(testDataFilePath: String) {
        val fileName = testDataFilePath.substringAfterLast('/')
        val fileText = dataFile(fileName).readText()
        withCustomCompilerOptions(fileText, project, module) {
            val file = myFixture.configureByFile(fileName)
            val newName = InTextDirectivesUtils.findStringWithPrefixes(file.text, "// NEW_NAME: ")
                ?: error("NEW_NAME is not specified")
            val expectedConflict = InTextDirectivesUtils.findStringWithPrefixes(file.text, "// SHOULD_FAIL_WITH: ")
            val handler = LanguageRefactoringSupport.getInstance().forContext(file)?.changeSignatureHandler
                ?: error("Change signature handler is not available")
            val element = handler.findTargetMember(file, editor) as KtElement
            val declaration = KotlinChangeSignatureHandler.findDeclaration(element, element, project, editor) as KtNamedDeclaration
            val changeInfo = KotlinChangeInfo(KotlinMethodDescriptor(declaration)).apply { this.newName = newName }

            try {
                KotlinChangeSignatureProcessor(project, changeInfo).run()
            }
            catch (e: BaseRefactoringProcessor.ConflictsInTestsException) {
                assertEquals(expectedConflict, e.messages.joinToString())
                return@withCustomCompilerOptions
            }

            if (expectedConflict != null) {
                fail("Expected conflict: $expectedConflict")
            }
            myFixture.checkResultByFile(dataFile("$fileName.after"))
            checkMarkedRenameResult(file as KtFile)
        }
    }
}
