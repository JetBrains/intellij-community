// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiReference
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.impl.source.PsiClassReferenceType
import org.jetbrains.kotlin.nj2k.externalCodeProcessing.OriginalJavaPsiContext

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

internal class OriginalJavaSemanticResolver(
    private val originalJavaPsiContext: OriginalJavaPsiContext = OriginalJavaPsiContext.Empty,
) {
    internal inline fun <reified T : PsiElement> originalElement(element: T): T? =
        element.originalAnchoredElement() as? T

    internal inline fun <reified T : PsiElement> originalElementOrSelf(element: T): T =
        originalElement(element) ?: element

    fun resolveReference(reference: PsiReference): PsiElement? =
        reference.resolve() ?: reference.originalReference()?.resolve()

    fun resolveClassType(type: PsiClassType): PsiClass? =
        (type.resolve() ?: (type as? PsiClassReferenceType)?.reference?.let(::resolveReference)) as? PsiClass

    fun resolveMember(member: PsiMember): PsiMember? =
        originalJavaPsiContext.resolve(member)

    fun resolveClass(psiClass: PsiClass): PsiClass? =
        originalJavaPsiContext.resolve(psiClass)

    private fun PsiReference.originalReference(): PsiReference? {
        val originalElement = element.originalAnchoredElement() ?: return null
        val copiedReferences = element.references
        val originalReferences = originalElement.references
        if (copiedReferences.size == originalReferences.size) {
            val index = copiedReferences.indexOfFirst { it === this }
            if (index >= 0) return originalReferences[index]
        }
        return originalReferences.firstOrNull { it.rangeInElement == rangeInElement }
            ?: originalReferences.singleOrNull()
    }
}

private fun PsiElement.originalAnchoredElement(): PsiElement? =
    getUserData(originalPsiPointerKey)?.element
