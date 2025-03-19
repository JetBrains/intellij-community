// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.changeSignature

import com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.impl.source.PsiMethodImpl
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.util.CommonRefactoringUtil.RefactoringErrorHintException
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.KaDiagnosticCheckerFilter
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.descriptors.Visibility
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.codeinsight.utils.AddQualifiersUtil
import org.jetbrains.kotlin.idea.k2.refactoring.checkSuperMethods
import org.jetbrains.kotlin.idea.refactoring.changeSignature.BaseKotlinChangeSignatureTest
import org.jetbrains.kotlin.psi.*

class KotlinFirChangeSignatureTest :
    BaseKotlinChangeSignatureTest<KotlinChangeInfo, KotlinParameterInfo, KotlinTypeInfo, Visibility, KotlinMethodDescriptor>() {

    override val pluginMode: KotlinPluginMode
        get() = KotlinPluginMode.K2

    override fun getSuffix(): String {
        return "k2"
    }

    override fun checkErrorsAfter(): Boolean {
        return false // todo
    }

    override fun doTestInvokePosition(code: String) {
        doTestTargetElement<KtNamedFunction>(code)
    }

    override fun addFullQualifier(fragment: KtExpressionCodeFragment) {
        AddQualifiersUtil.addQualifiersRecursively(fragment)
    }

    override fun KotlinChangeInfo.createKotlinParameter(
        name: String,
        originalType: String?,
        defaultValueForCall: KtExpression?,
        defaultValueAsDefaultParameter: Boolean,
        currentType: String?
    ): KotlinParameterInfo = KotlinParameterInfo(
        name = name,
        originalType = createParameterTypeInfo(originalType, method),
        defaultValueForCall = defaultValueForCall,
        defaultValueAsDefaultParameter = defaultValueAsDefaultParameter,
        valOrVar = defaultValOrVar(method),
        defaultValue = defaultValueForCall?.takeIf { defaultValueAsDefaultParameter },
        context = method
    ).apply {
        if (currentType != null) {
            setType(currentType)
        }
    }

    override fun createParameterTypeInfo(type: String?, ktElement: PsiElement): KotlinTypeInfo = KotlinTypeInfo(type, ktElement as KtElement)

    override fun createChangeInfo(): KotlinChangeInfo {
        val element = findTargetElement()?.unwrapped as KtElement
        val targetElement = KotlinChangeSignatureHandler.findDeclaration(element, element, project, editor) as KtNamedDeclaration
        val superMethod = (checkSuperMethods(targetElement, emptyList(), RefactoringBundle.message("to.refactor")).first() as KtNamedDeclaration).takeIf { !file.name.contains("OverriderOnly") } ?: targetElement
        return KotlinChangeInfo(KotlinMethodDescriptor(superMethod))
    }

    override fun doRefactoring(configure: KotlinChangeInfo.() -> Unit) {
        KotlinChangeSignatureProcessor(project, createChangeInfo().apply { configure() }).run()
    }

    override fun ignoreTestData(fileName: String): Boolean {
        return fileName.contains("Propagat")
    }

    override fun getIgnoreDirective(): String {
        return "// IGNORE_K2"
    }


    //--------------------------------------------------
    fun testJavaMethodJvmStaticKotlinUsages() = doTest {
        swapParameters(0, 1)
    }

    private inline fun <reified T> doTestTargetDeclaration(code: String, name: String) {
        myFixture.configureByText("dummy.kt", code)
        val element = findTargetElement() as KtElement
        val declaration = KotlinChangeSignatureHandler.findDeclaration(element, element, project, editor)!!
        assertEquals(T::class, declaration::class)
        assertEquals(name, (declaration as PsiNamedElement).name)
    }

    fun testJavaTarget() {
        myFixture.addClass("public class A { public void fooBar() {} }")
        doTestTargetDeclaration<PsiMethodImpl>("class B { fun m(a: A) { a.fooB<caret>ar() } }", "fooBar")
    }

    fun testKotlinTarget() {
        doTestTargetDeclaration<KtNamedFunction>("class B { fun m() { fooB<caret>ar() } fun fooBar(){} }", "fooBar")
    }

    private fun doTestConflict(code: String, conflict: String) {
        myFixture.configureByText("dummy.kt", code)
        val element = findTargetElement() as KtElement
        try {
            KotlinChangeSignatureHandler.findDeclaration(element, element, project, editor)
        } catch (e: RefactoringErrorHintException) {
            assertEquals(conflict, e.message)
            return
        }
        fail("Expected conflict message")
    }

    fun testStdlib() {
        doTestConflict("fun main() { lis<caret>tOf(\"\") } ", "Cannot perform refactoring.\nLibrary declarations cannot be changed")
    }

    fun testInterface() {
        doTestConflict("interface <caret>A {}", "Cannot perform refactoring.\nThe caret should be positioned at the name of the function or constructor to be refactored.")
    }

    fun testLocalVariable() {
        doTestConflict("fun main() { val <caret>x = 42 }", "Cannot perform refactoring.\nThe caret should be positioned at the name of the function or constructor to be refactored.")
    }

    @OptIn(KaExperimentalApi::class, KaAllowAnalysisOnEdt::class)
    fun testExpressionFragmentErrors() {
        val psiFile = myFixture.addFileToProject("CommonList.kt", "class CustomList<in T>")
        val fragment = KtPsiFactory(project).createTypeCodeFragment("CustomList<out String>", psiFile)
        assertTrue(
            allowAnalysisOnEdt {
                analyze(fragment) {
                    fragment.collectDiagnostics(KaDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS).isNotEmpty()
                }
            }
        )
    }

    @OptIn(KaExperimentalApi::class, KaAllowAnalysisOnEdt::class)
    fun testTypeFragmentErrors() {
        val psiFile = myFixture.addFileToProject("CommonList.kt", "class CustomList<in T>")
        val fragment = KtPsiFactory(project).createTypeCodeFragment("", psiFile)
        val diagnostics = allowAnalysisOnEdt {
            analyze(fragment) {
                fragment.collectDiagnostics(KaDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS)
                    .map { it.defaultMessage}
            }
        }
        assertNotEmpty(diagnostics)
        assertEquals("Unexpected diagnostics number", 1, diagnostics.size)
        assertEquals("Syntax error: Incomplete code.", diagnostics.first())

        runWriteCommandAction(project) {
            fragment.containingFile.fileDocument.setText("CustomList<String>")
            PsiDocumentManager.getInstance(project).commitDocument(fragment.containingFile.fileDocument)
        }

        assertTrue(
            allowAnalysisOnEdt {
                analyze(fragment) {
                    fragment.collectDiagnostics(KaDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS).isEmpty()
                }
            }
        )
    }

    override fun testRemoveDataClassParameter() {
        runAndCheckConflicts {
            super.testRemoveDataClassParameter()
        }
    }

    override fun testRemoveAllOriginalDataClassParameters() {
        runAndCheckConflicts {
            super.testRemoveAllOriginalDataClassParameters()
        }
    }
}