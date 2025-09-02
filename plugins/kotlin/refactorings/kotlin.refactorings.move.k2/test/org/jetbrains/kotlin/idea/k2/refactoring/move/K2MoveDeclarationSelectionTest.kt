// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.refactoring.move

import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty

class K2MoveDeclarationSelectionTest : KotlinLightCodeInsightFixtureTestCase() {
    override val pluginMode: KotlinPluginMode
        get() = KotlinPluginMode.K2

    fun `test end of function`() {
        doTest("""
            fun fn() {}<caret>
            
        """.trimIndent()) { assertMovedElementType<KtNamedFunction>(it) }
    }

    fun `test function name`() {
        doTest("""
            fun f<caret>n() {}
            
        """.trimIndent()) { assertMovedElementType<KtNamedFunction>(it) }
    }

    fun `test function keyword`() {
        doTest("""
            fu<caret>n fn() {}
            
        """.trimIndent()) { assertMovedElementType<KtNamedFunction>(it) }
    }

    fun `test function start`() {
        doTest("""
            <caret>fun fn() {}
            
        """.trimIndent()) { assertMovedElementType<KtNamedFunction>(it) }
    }

    fun `test end of property`() {
        doTest("""
            var prop = 0<caret>
            
        """.trimIndent()) { assertMovedElementType<KtProperty>(it) }
    }

    fun `test property name`() {
        doTest("""
            var <caret>prop = 0
            
        """.trimIndent()) { assertMovedElementType<KtProperty>(it) }
    }

    fun `test property start`() {
        doTest("""
            <caret>var prop = 0
            
        """.trimIndent()) { assertMovedElementType<KtProperty>(it) }
    }

    fun `test end of class`() {
        doTest("""
            class Cls {}<caret>
            
        """.trimIndent()) { assertMovedElementType<KtClass>(it) }
    }

    fun `test class name`() {
        doTest("""
            class <caret>Cls {}
            
        """.trimIndent()) { assertMovedElementType<KtClass>(it) }
    }

    fun `test class start`() {
        doTest("""
            <caret>class Cls {}
            
        """.trimIndent()) { assertMovedElementType<KtClass>(it) }
    }

    fun `test end of document`() {
        doTest("""fun fn() {}<caret>""") { assertMovedElementType<KtNamedFunction>(it) }
    }

    fun `test next line`() {
        doTest("""
            fun fn() {}
            <caret>
        """.trimIndent()) { assertMovedElementType<KtFile>(it) }
    }

    fun `test between declarations`() {
        doTest("""
            fun fn1() {}
            <caret>
            fun fn2() {}
        """.trimIndent()) { assertMovedElementType<KtFile>(it) }
    }


    private fun doTest(kotlinFileText: String, checkResult: (elementToMove: PsiElement?) -> Unit) {
        myFixture.configureByText(KotlinFileType.INSTANCE, kotlinFileText)
        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(myFixture.editor.document)
            ?: error("PsiFile not found")
        val elementAtCaret = psiFile.findElementAt(myFixture.caretOffset) ?: psiFile
        val elementToMove = with(K2MoveHandler()) {
            elementAtCaret.findElementToMove(myFixture.editor)
        }
        checkResult(elementToMove)
    }

    private inline fun <reified T : KtElement> assertMovedElementType(element: PsiElement?) {
        assert(element is T) {
            "Expected the moved element to be of type ${T::class.simpleName}, but got ${element?.let { it::class.simpleName }} instead"
        }
    }
}
