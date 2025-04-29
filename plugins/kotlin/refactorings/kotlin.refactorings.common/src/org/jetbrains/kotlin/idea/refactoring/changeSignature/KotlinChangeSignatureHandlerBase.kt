// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.changeSignature

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.changeSignature.ChangeSignatureHandler
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.utils.KotlinSupportAvailability
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.utils.exceptions.checkWithAttachment
import org.jetbrains.kotlin.utils.exceptions.withPsiEntry

abstract class KotlinChangeSignatureHandlerBase : ChangeSignatureHandler {
    protected enum class InapplicabilityKind(val description: String) {
        Varargs(KotlinBundle.message("error.cant.refactor.vararg.functions")),
        Library(KotlinBundle.message("error.hint.library.declarations.cannot.be.changed")),
        Synthetic(KotlinBundle.message("cannot.refactor.synthesized.function")),
        Dynamic(KotlinBundle.message("message.change.signature.is.not.applicable.to.dynamically.invoked.functions")),
        JavaCallable(RefactoringBundle.message("error.wrong.caret.position.method.or.class.name")),
        Null(KotlinBundle.message("error.wrong.caret.position.function.or.constructor.name"))
    }

    abstract fun asInvokeOperator(call: KtCallElement?): PsiElement?
    abstract fun invokeChangeSignature(element: KtElement, context: PsiElement, project: Project, editor: Editor?, dataContext: DataContext?)

    override fun findTargetMember(element: PsiElement) = findTargetForRefactoring(element)

    override fun invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext) {
        editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)

        val element = findTargetMember(file, editor) ?: CommonDataKeys.PSI_ELEMENT.getData(dataContext) ?: return
        val elementAtCaret = file.findElementAt(editor.caretModel.offset) ?: return
        checkWithAttachment(element is KtElement, {"This handler must be invoked for Kotlin elements only: ${element::class.java}"}) {
            withPsiEntry("element.kt", element)
        }

        if (!KotlinSupportAvailability.isSupported(element)) return

        invokeChangeSignature(element, elementAtCaret as KtElement, project, editor, dataContext)
    }

    override fun invoke(project: Project, elements: Array<PsiElement>, dataContext: DataContext?) {
        val element = elements.singleOrNull()?.unwrapped ?: return
        checkWithAttachment(element is KtElement, { "This handler must be invoked for Kotlin elements only: ${element::class.java}" }) {
            withPsiEntry("element", element)
        }

        if (!KotlinSupportAvailability.isSupported(element)) return

        val editor = dataContext?.let { CommonDataKeys.EDITOR.getData(it) }
        val context = dataContext?.let { CommonDataKeys.PSI_FILE.getData(it) } ?: element
        invokeChangeSignature(element, context, project, editor, dataContext)
    }

    override fun getTargetNotFoundMessage() = KotlinBundle.message("error.wrong.caret.position.function.or.constructor.name")

    fun findTargetForRefactoring(element: PsiElement): PsiElement? {
        val elementParent = element.parent
        if ((elementParent is KtNamedFunction || elementParent is KtClass || elementParent is KtProperty) && (elementParent as KtNamedDeclaration).nameIdentifier === element) return elementParent

        if (elementParent is KtParameter && elementParent.hasValOrVar() && elementParent.parentOfType<KtPrimaryConstructor>()?.valueParameterList === elementParent.parent) return elementParent

        if (elementParent is KtProperty && elementParent.valOrVarKeyword === element) return elementParent
        if (elementParent is KtConstructor<*> && elementParent.getConstructorKeyword() === element) return elementParent

        element.parentOfType<KtDestructuringDeclaration>()?.let { return null }

        element.parentOfType<KtParameterList>()?.let { parameterList ->
            return PsiTreeUtil.getParentOfType(parameterList, KtFunction::class.java, KtProperty::class.java, KtClass::class.java)
        }

        element.parentOfType<KtContextReceiverList>()?.let { contextReceiverList ->
            return PsiTreeUtil.getParentOfType(contextReceiverList, KtFunction::class.java, KtProperty::class.java)
        }

        element.parentOfType<KtTypeParameterList>()?.let { typeParameterList ->
            return PsiTreeUtil.getParentOfType(typeParameterList, KtFunction::class.java, KtProperty::class.java, KtClass::class.java)
        }

        val call: KtCallElement? = PsiTreeUtil.getParentOfType(
            element, KtCallExpression::class.java, KtSuperTypeCallEntry::class.java, KtConstructorDelegationCall::class.java
        )

        val calleeExpr = call?.calleeExpression?.let { callee ->
            (callee as? KtConstructorCalleeExpression)?.constructorReferenceExpression ?: callee
        } ?: element.parentOfType<KtSimpleNameExpression>()

        if (calleeExpr is KtSimpleNameExpression || calleeExpr is KtConstructorDelegationReferenceExpression) {
            asInvokeOperator(call)?.let { return it }
            return calleeExpr
        }

        return null
    }

}
