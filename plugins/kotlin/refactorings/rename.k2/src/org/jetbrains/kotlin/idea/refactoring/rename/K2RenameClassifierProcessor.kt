// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.rename

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject

internal class K2RenameClassifierProcessor : RenamePsiElementProcessor() {
    override fun canProcessElement(element: PsiElement): Boolean {
        val unwrapped = element.unwrapped
        return unwrapped is KtClassOrObject || unwrapped is KtConstructor<*>
    }

    override fun substituteElementToRename(element: PsiElement, editor: Editor?): PsiElement? {
        return when (val unwrapped = element.unwrapped) {
            is KtConstructor<*> -> unwrapped.containingClassOrObject
            is KtClassOrObject -> unwrapped
            else -> null
        }
    }
}