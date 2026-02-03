// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.rename

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.command.CommandProcessor
import com.intellij.psi.PsiFile
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.rename.inplace.VariableInplaceRenameHandler
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.CodeInsightTestUtil
import junit.framework.TestCase
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.refactoring.rename.handlers.RenameKotlinImplicitLambdaParameter
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.createTextEditorBasedDataContext
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

abstract class AbstractInplaceRenameTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun getProjectDescriptor(): LightProjectDescriptor =
        KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()

    override fun setUp() {
        super.setUp()
        myFixture.addClass("interface JavaInterface { void foo(); } ")
        myFixture.addFileToProject("KotlinInterface.kt", "interface KotlinInterface")
    }

    open fun doTest(unused: String) {
        IgnoreTests.runTestIfNotDisabledByFileDirective(
            dataFilePath(),
            IgnoreTests.DIRECTIVES.of(pluginMode),
            directivePosition = IgnoreTests.DirectivePosition.LAST_LINE_IN_FILE
        ) {
            doTestWithoutIgnoreDirective(unused)
        }
    }

    protected fun doTestWithoutIgnoreDirective(unused: String) {
        val fileName = fileName()
        val file = myFixture.configureByFile(fileName)
        val newName = InTextDirectivesUtils.findStringWithPrefixes(file.text, "// NEW_NAME: ")
        val dataContext = createTextEditorBasedDataContext(project, editor, editor.caretModel.currentCaret)
        val renameDirective =
            InTextDirectivesUtils.findStringWithPrefixes(file.text, "// RENAME: ") ?: error("`RENAME` handler is not specified")

        val handler = when (renameDirective) {
            "member" -> KotlinMemberInplaceRenameHandler()
            "variable" -> KotlinVariableInplaceRenameHandler()
            "lambdaParameter" -> {
                doImplicitLambdaParameterTest(file, RenameKotlinImplicitLambdaParameter(), dataContext, newName)
                return
            }

            else -> error("unknown rename handler $renameDirective")
        }

        val element = TargetElementUtil.findTargetElement(
            editor,
            TargetElementUtil.ELEMENT_NAME_ACCEPTED or TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED
        ) ?: file.findElementForRename<KtNameReferenceExpression>(editor.caretModel.offset)

        assertNotNull(element)

        if (newName == null) {
            assertFalse("In-place rename is allowed for $element", handler.isRenaming(dataContext))
        } else {
            try {
                assertTrue("In-place rename not allowed for $element", handler.isRenaming(dataContext))
                var throwable: Throwable? = null
                CommandProcessor.getInstance().executeCommand(project, {
                    try {
                        CodeInsightTestUtil.doInlineRename(handler, newName, editor, element)
                    } catch (t: Throwable) {
                        throwable = t
                    }
                }, null, null)
                if (throwable != null) {
                    throw throwable!!
                }

                val nameSuffix = getAfterFileNameSuffix()
                var afterFile = dataFile("$fileName${nameSuffix ?: ""}.after")
                if (nameSuffix != null && !afterFile.exists()) {
                    afterFile = dataFile("$fileName.after")
                }
                myFixture.checkResultByFile(afterFile)
            } catch (e: BaseRefactoringProcessor.ConflictsInTestsException) {
                val expectedMessage = InTextDirectivesUtils.findStringWithPrefixes(file.text, "// SHOULD_FAIL_WITH: ")
                TestCase.assertEquals(expectedMessage, e.messages.joinToString())
            } catch (e: CommonRefactoringUtil.RefactoringErrorHintException) {
                val expectedMessage = InTextDirectivesUtils.findStringWithPrefixes(file.text, "// SHOULD_FAIL_WITH: ")
                TestCase.assertEquals(expectedMessage, e.message?.replace("\n", " "))
            }
        }
    }

    protected open fun getAfterFileNameSuffix(): String? = null

    private fun doImplicitLambdaParameterTest(
        file: PsiFile,
        handler: VariableInplaceRenameHandler,
        dataContext: DataContext,
        newName: String?
    ) {
        val element = file.findElementForRename<KtNameReferenceExpression>(editor.caretModel.offset)!!
        assertNotNull(element)

        assertTrue("In-place rename not allowed for $element", handler.isRenaming(dataContext))

        val project = editor.project!!

        TemplateManagerImpl.setTemplateTesting(testRootDisposable)
        project.executeWriteCommand("rename test") {
            handler.invoke(project, editor, file, dataContext)
        }

        var state = TemplateManagerImpl.getTemplateState(editor)
        assert(state != null)
        val range = state!!.currentVariableRange
        assert(range != null)
        project.executeWriteCommand("replace string") {
            editor.document.replaceString(range!!.startOffset, range.endOffset, newName!!)
        }

        state = TemplateManagerImpl.getTemplateState(editor)
        assert(state != null)
        state!!.gotoEnd(false)

        myFixture.checkResultByFile(dataFile("${fileName()}.after"))
        return
    }
}