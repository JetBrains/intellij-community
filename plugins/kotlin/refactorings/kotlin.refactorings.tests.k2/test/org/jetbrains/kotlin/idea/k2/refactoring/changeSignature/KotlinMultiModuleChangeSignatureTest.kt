// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.changeSignature

import com.intellij.psi.PsiDocumentManager
import com.intellij.refactoring.BaseRefactoringProcessor.ConflictsInTestsException
import com.intellij.refactoring.RefactoringBundle
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.idea.k2.refactoring.checkSuperMethods
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinValVar
import org.jetbrains.kotlin.idea.test.*
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class KotlinMultiModuleChangeSignatureTest : KotlinMultiFileTestCase(),
                                             ExpectedPluginModeProvider {

    init {
        isMultiModule = true
    }

    override val pluginMode: KotlinPluginMode
        get() = KotlinPluginMode.K2

    override fun setUp() {
        setUpWithKotlinPlugin { super.setUp() }
    }

    override fun getTestRoot(): String = "/refactoring/changeSignatureMultiModule/"

    override fun getTestDataDirectory() = IDEA_TEST_DATA_DIR

    private fun doTest(filePath: String, configure: KotlinChangeInfo.() -> Unit) {
        doTestCommittingDocuments { rootDir, _ ->
            val psiFile = rootDir.findFileByRelativePath(filePath)!!.toPsiFile(project)!!
            val doc = PsiDocumentManager.getInstance(project).getDocument(psiFile)!!
            val marker = doc.extractMarkerOffset(project)
            assert(marker != -1)
            val element = KotlinChangeSignatureHandler.findTargetMember(psiFile.findElementAt(marker)!!)?.unwrapped as KtElement
            val targetElement = KotlinChangeSignatureHandler.findDeclaration(element, element, project, editor) as KtNamedDeclaration
            val superMethod = checkSuperMethods(targetElement, emptyList(), RefactoringBundle.message("to.refactor")).first() as KtNamedDeclaration
            val changeInfo = KotlinChangeInfo(KotlinMethodDescriptor(superMethod))
            KotlinChangeSignatureProcessor(project, changeInfo.apply { configure() }).run()
        }
    }

    private fun KotlinChangeInfo.addParameter(name: String, type: String, defaultValue: String) {
        addParameter(
            KotlinParameterInfo(
                name = name,
                originalType = KotlinTypeInfo(type, method),
                valOrVar = defaultValOrVar(method),
                defaultValue = null,
                defaultValueForCall = KtPsiFactory(project).createExpression(defaultValue),
                defaultValueAsDefaultParameter = false,
                context = method
            )
        )
    }

    fun testHeadersAndImplsByHeaderFun() = doTest("Common/src/test/test.kt") {
        newName = "baz"
    }

    fun testHeadersAndImplsByImplFun() = doTest("JS/src/test/test.kt") {
        newName = "baz"
    }

    fun testHeadersAndImplsByHeaderClassMemberFun() = doTest("Common/src/test/test.kt") {
        newName = "baz"
    }

    fun testHeadersAndImplsByImplClassMemberFun() = doTest("JS/src/test/test.kt") {
        newName = "baz"
    }

    fun testHeaderPrimaryConstructorNoParams() = doTest("Common/src/test/test.kt") {
        addParameter("n", "Int", "1")
    }

    fun testHeaderPrimaryConstructor() = doTest("Common/src/test/test.kt") {
        addParameter("b", "Boolean", "false")
    }

    fun testHeaderSecondaryConstructor() = doTest("Common/src/test/test.kt") {
        addParameter("b", "Boolean", "false")
    }

    fun testJavaUsage() = ConflictsInTestsException.withIgnoredConflicts<Throwable> {
        doTest("Common/src/test/test.kt") {
            addParameter("b", "Boolean", "false")
        }
    }

    fun testJavaConstructorUsage() = ConflictsInTestsException.withIgnoredConflicts<Throwable> {
        doTest("Common/src/test/test.kt") {
            addParameter("b", "Boolean", "false")
        }
    }

    //have explicit primary constructor in common and only implicit constructor in jvm
    fun testJavaConstructorUsage1() = ConflictsInTestsException.withIgnoredConflicts<Throwable> {
        doTest("Common/src/test/test.kt") {
            addParameter("b", "Boolean", "false")
        }
    }

    fun testKeepValVarInPlatform() = doTest("Common/src/test/test.kt") {
        newParameters[0].valOrVar = KotlinValVar.None
        newParameters[0].currentType = KotlinTypeInfo("Boolean", method)
    }

    fun testImplPrimaryConstructorNoParams() = doTest("JVM/src/test/test.kt") {
        addParameter("n", "Int", "1")
    }

    fun testImplPrimaryConstructor() = doTest("JVM/src/test/test.kt") {
        addParameter("b", "Boolean", "false")
    }

    fun testImplSecondaryConstructor() = doTest("JS/src/test/test.kt") {
        addParameter("b", "Boolean", "false")
    }

    fun testSuspendImpls() = doTest("Common/src/test/test.kt") {
        addParameter("n", "Int", "0")
    }
}
