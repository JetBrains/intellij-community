// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.introduce.extractClass

import com.intellij.java.refactoring.JavaRefactoringBundle
import com.intellij.psi.PsiElement
import com.intellij.refactoring.RefactoringBundle
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.refactoring.introduce.extractClass.ui.KotlinExtractSuperclassDialog
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject

@ApiStatus.Internal
object KotlinExtractSuperclassHandler : KotlinExtractSuperHandlerBase(false) {
    val REFACTORING_NAME: String
        @Nls
        get() = KotlinBundle.message("text.extract.superclass")

    override fun getErrorMessage(klass: KtClassOrObject): String? {
        val superMessage = super.getErrorMessage(klass)
        if (superMessage != null) return superMessage
        if (klass is KtClass) {
            if (klass.isInterface()) return RefactoringBundle.message("superclass.cannot.be.extracted.from.an.interface")
            if (klass.isEnum()) return JavaRefactoringBundle.message("superclass.cannot.be.extracted.from.an.enum")
            if (klass.isAnnotation()) return KotlinBundle.message("error.text.superclass.cannot.be.extracted.from.an.annotation.class")
        }
        return null
    }

    override fun createDialog(klass: KtClassOrObject, targetParent: PsiElement): KotlinExtractSuperclassDialog =
        KotlinExtractSuperclassDialog(
            originalClass = klass,
            targetParent = targetParent,
            conflictChecker = { checkConflicts(klass, it) },
            refactoring = { KotlinExtractSuperRefactoring.getInstance().performRefactoring(it) }
        )
}