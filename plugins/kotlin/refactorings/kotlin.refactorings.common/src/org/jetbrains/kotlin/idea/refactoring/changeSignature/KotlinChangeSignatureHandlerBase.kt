// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.changeSignature

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import com.intellij.refactoring.HelpID
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.changeSignature.ChangeSignatureHandler
import com.intellij.refactoring.changeSignature.ChangeSignatureUtil
import com.intellij.refactoring.util.CommonRefactoringUtil
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.utils.checkWithAttachment

abstract class KotlinChangeSignatureHandlerBase<T> : ChangeSignatureHandler {
    abstract fun asInvokeOperator(call: KtCallElement?): PsiElement?
    abstract fun referencedClassOrCallable(calleeExpr: KtReferenceExpression): PsiElement?
    abstract fun findDescriptor(element: KtElement, project: Project, editor: Editor?): T?
    abstract fun isVarargFunction(function: T): Boolean
    abstract fun isSynthetic(function: T, context: KtElement): Boolean
    abstract fun isLibrary(function: T, context: KtElement): Boolean
    abstract fun isJavaCallable(function: T): Boolean
    abstract fun isDynamic(function: T): Boolean
    abstract fun getDeclaration(t: T, project: Project): PsiElement?
    abstract fun getDeclarationName(t: T): String
    abstract fun runChangeSignature(project: Project, editor: Editor?, callableDescriptor: T, context: PsiElement)

    override fun findTargetMember(element: PsiElement) = findTargetForRefactoring(element)

    override fun invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext) {
        editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)

        val element = findTargetMember(file, editor) ?: CommonDataKeys.PSI_ELEMENT.getData(dataContext) ?: return
        val elementAtCaret = file.findElementAt(editor.caretModel.offset) ?: return
        checkWithAttachment(element is KtElement, {"This handler must be invoked for Kotlin elements only: ${element::class.java}"}) {
            it.withAttachment("element", element)
        }

        invokeChangeSignature(element, elementAtCaret as KtElement, project, editor)
    }

    override fun invoke(project: Project, elements: Array<PsiElement>, dataContext: DataContext?) {
        val element = elements.singleOrNull()?.unwrapped ?: return
        checkWithAttachment(element is KtElement, { "This handler must be invoked for Kotlin elements only: ${element::class.java}" }) {
            it.withAttachment("element", element)
        }

        val editor = dataContext?.let { CommonDataKeys.EDITOR.getData(it) }
        invokeChangeSignature(element, element, project, editor)
    }

    override fun getTargetNotFoundMessage() = KotlinBundle.message("error.wrong.caret.position.function.or.constructor.name")

    fun findTargetForRefactoring(element: PsiElement): PsiElement? {
        val elementParent = element.parent
        if ((elementParent is KtNamedFunction || elementParent is KtClass || elementParent is KtProperty) && (elementParent as KtNamedDeclaration).nameIdentifier === element) return elementParent

        if (elementParent is KtParameter && elementParent.hasValOrVar() && elementParent.parentOfType<KtPrimaryConstructor>()?.valueParameterList === elementParent.parent) return elementParent

        if (elementParent is KtProperty && elementParent.valOrVarKeyword === element) return elementParent
        if (elementParent is KtConstructor<*> && elementParent.getConstructorKeyword() === element) return elementParent

        element.parentOfType<KtParameterList>()?.let { parameterList ->
            return PsiTreeUtil.getParentOfType(parameterList, KtFunction::class.java, KtProperty::class.java, KtClass::class.java)
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

            @Suppress("USELESS_CAST")
            return referencedClassOrCallable(calleeExpr as KtReferenceExpression)
        }

        return null
    }


    fun invokeChangeSignature(element: KtElement, context: KtElement, project: Project, editor: Editor?) {

        val callableDescriptor = findDescriptor(element, project, editor)
        if (!checkDescriptor(callableDescriptor, project, editor, context)) return

        require(callableDescriptor != null)
        runChangeSignature(project, editor, callableDescriptor, context)
    }


    fun checkDescriptor(callableDescriptor: T?, project: Project, editor: Editor?, context: KtElement): Boolean {
        if (callableDescriptor == null) {
            val message = RefactoringBundle.getCannotRefactorMessage(
              KotlinBundle.message("error.wrong.caret.position.function.or.constructor.name")
            )

            CommonRefactoringUtil.showErrorHint(
              project,
              editor,
              message,
              RefactoringBundle.message("changeSignature.refactoring.name"),
              HelpID.CHANGE_SIGNATURE
            )
            return false
        }
        if (isVarargFunction(callableDescriptor)) {
            val message = KotlinBundle.message("error.cant.refactor.vararg.functions")
            CommonRefactoringUtil.showErrorHint(
              project,
              editor,
              message,
              RefactoringBundle.message("changeSignature.refactoring.name"),
              HelpID.CHANGE_SIGNATURE
            )
            return false
        }

        if (isSynthetic(callableDescriptor, context)) {
            val message = KotlinBundle.message("cannot.refactor.synthesized.function", getDeclarationName(callableDescriptor))
            CommonRefactoringUtil.showErrorHint(
              project,
              editor,
              message,
              RefactoringBundle.message("changeSignature.refactoring.name"),
              HelpID.CHANGE_SIGNATURE
            )
            return false
        }

        if (isLibrary(callableDescriptor, context)) {
            CommonRefactoringUtil.showErrorHint(
              project,
              editor,
              RefactoringBundle.getCannotRefactorMessage(KotlinBundle.message("error.hint.library.declarations.cannot.be.changed")),
              RefactoringBundle.message("changeSignature.refactoring.name"),
              "refactoring.changeSignature",
            )

            return false
        }

        if (isJavaCallable(callableDescriptor)) {
            val declaration = getDeclaration(callableDescriptor, project)
            if (declaration is PsiClass) {
                val message = RefactoringBundle.getCannotRefactorMessage(
                  RefactoringBundle.message("error.wrong.caret.position.method.or.class.name")
                )
                CommonRefactoringUtil.showErrorHint(
                  project,
                  editor,
                  message,
                  RefactoringBundle.message("changeSignature.refactoring.name"),
                  "refactoring.changeSignature",
                )
                return false
            }
            assert(declaration is PsiMethod) { "PsiMethod expected: $callableDescriptor" }
            ChangeSignatureUtil.invokeChangeSignatureOn(declaration as PsiMethod, project)
            return false
        }

        if (isDynamic(callableDescriptor)) {
            if (editor != null) {
                CommonRefactoringUtil.showErrorHint(
                  project,
                  editor,
                  KotlinBundle.message("message.change.signature.is.not.applicable.to.dynamically.invoked.functions"),
                  RefactoringBundle.message("changeSignature.refactoring.name"),
                  null
                )
            }
            return false
        }
        return true
    }


}
