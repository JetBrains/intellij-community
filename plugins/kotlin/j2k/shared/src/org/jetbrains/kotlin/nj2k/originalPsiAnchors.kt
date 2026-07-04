// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.PsiClassReferenceType
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer

private val originalPsiPointerKey = Key.create<SmartPsiElementPointer<PsiElement>>("j2k.original.psi.pointer")

internal fun anchorCopiedElementToOriginal(original: PsiElement, copied: PsiElement) {
    copied.putUserData(originalPsiPointerKey, SmartPointerManager.createPointer(original))

    var originalChild = original.firstChild
    var copiedChild = copied.firstChild
    while (originalChild != null && copiedChild != null) {
        anchorCopiedElementToOriginal(originalChild, copiedChild)
        originalChild = originalChild.nextSibling
        copiedChild = copiedChild.nextSibling
    }
}

internal inline fun <reified T : PsiElement> PsiElement.originalElement(): T? =
    getUserData(originalPsiPointerKey)?.element as? T

internal inline fun <reified T : PsiElement> T.originalElementOrSelf(): T =
    originalElement<T>() ?: this

internal fun PsiReference.resolveWithOriginalFallback(): PsiElement? =
    resolve() ?: originalReference()?.resolve()

internal fun PsiClassType.resolveWithOriginalFallback(): PsiElement? =
    resolve() ?: (this as? PsiClassReferenceType)?.reference?.resolveWithOriginalFallback()

private fun PsiReference.originalReference(): PsiReference? {
    val originalElement = element.originalElement<PsiElement>() ?: return null
    val copiedReferences = element.references
    val originalReferences = originalElement.references
    if (copiedReferences.size == originalReferences.size) {
        val index = copiedReferences.indexOfFirst { it === this }
        if (index >= 0) return originalReferences[index]
    }
    return originalReferences.firstOrNull { it.rangeInElement == rangeInElement }
        ?: originalReferences.singleOrNull()
}
