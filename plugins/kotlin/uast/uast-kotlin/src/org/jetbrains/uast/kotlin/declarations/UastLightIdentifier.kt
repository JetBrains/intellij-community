// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin.declarations

import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.kotlin.asJava.elements.KtLightIdentifier
import org.jetbrains.kotlin.kdoc.psi.impl.KDocName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.utils.KotlinExceptionWithAttachments
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UIdentifier
import org.jetbrains.uast.kotlin.createKDocNameSimpleNameReference
import org.jetbrains.uast.kotlin.unwrapFakeFileForLightClass
import org.jetbrains.uast.toUElement

class UastLightIdentifier(lightOwner: PsiNameIdentifierOwner, ktDeclaration: KtDeclaration?) :
    KtLightIdentifier(lightOwner, ktDeclaration) {
    override fun getContainingFile(): PsiFile = unwrapFakeFileForLightClass(super.getContainingFile())
}

class KotlinUIdentifier constructor(
    javaPsiSupplier: () -> PsiElement?,
    override val sourcePsi: PsiElement?,
    givenParent: UElement?
) : UIdentifier(sourcePsi, givenParent) {

    override val javaPsi: PsiElement? by lazy(javaPsiSupplier) // don't know any real need to call it in production

    override val psi: PsiElement?
        get() = javaPsi ?: sourcePsi

    init {
        if (ApplicationManager.getApplication().isUnitTestMode && !acceptableSourcePsi(sourcePsi))
            throw KotlinExceptionWithAttachments("sourcePsi should be physical leaf element but got $sourcePsi of (${sourcePsi?.javaClass})")
                .withAttachment("sourcePsi.text", sourcePsi?.text)
    }

    private fun acceptableSourcePsi(sourcePsi: PsiElement?): Boolean {
        if (sourcePsi == null) return true
        if (sourcePsi is LeafPsiElement) return true
        if (sourcePsi is KtElement && sourcePsi.firstChild == null) return true
        if (sourcePsi is KtStringTemplateExpression && sourcePsi.parent is KtCallExpression) return true // string literals could be identifiers of calls e.g. `"main" {}` in gradle.kts
        return false
    }

    override val uastParent: UElement? by lazy {
        if (givenParent != null) return@lazy givenParent
        val parent = sourcePsi?.parent ?: return@lazy null

        return@lazy if (parent is KDocName && parent.getQualifier() != null) // e.g. for UElement in org.jetbrains.uast.UElement
            createKDocNameSimpleNameReference(parentKDocName = parent, givenParent = null)
        else
            getIdentifierParentForCall(parent) ?: parent.toUElement()
    }

    private fun getIdentifierParentForCall(parent: PsiElement): UElement? {
        val parentParent = parent.parent
        if (parentParent is KtCallElement && parentParent.calleeExpression == parent) { // method identifiers in calls
            return parentParent.toUElement()
        }
        (generateSequence(parent) { it.parent }.take(3).find { it is KtTypeReference && it.parent is KtConstructorCalleeExpression })?.let {
            return it.parent.parent.toUElement()
        }
        return null
    }

    constructor(javaPsi: PsiElement?, sourcePsi: PsiElement?, uastParent: UElement?) : this({ javaPsi }, sourcePsi, uastParent)
    constructor(sourcePsi: PsiElement?, uastParent: UElement?) : this({ null }, sourcePsi, uastParent)
}