// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.kdoc.psi.impl.KDocName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.utils.KotlinExceptionWithAttachments
import org.jetbrains.uast.LazyParentUIdentifier
import org.jetbrains.uast.UElement
import org.jetbrains.uast.toUElement

@ApiStatus.Internal
class KotlinUIdentifier(
    private val javaPsiSupplier: () -> PsiElement?,
    override val sourcePsi: PsiElement?,
    givenParent: UElement?
) : LazyParentUIdentifier(sourcePsi, givenParent) {
    override val javaPsi: PsiElement?
        get() = javaPsiSupplier() // don't know any real need to call it in production

    override val psi: PsiElement?
        get() = javaPsi ?: sourcePsi

    init {
        if (ApplicationManager.getApplication().isUnitTestMode && !acceptableSourcePsi(sourcePsi))
            throw KotlinExceptionWithAttachments(
                "sourcePsi should be physical leaf element but got $sourcePsi of (${sourcePsi?.javaClass})"
            ).withAttachment("sourcePsi.text", sourcePsi?.text)
    }

    private fun acceptableSourcePsi(sourcePsi: PsiElement?): Boolean {
        if (sourcePsi == null) return true
        if (sourcePsi is LeafPsiElement) return true
        if (sourcePsi is KtElement && sourcePsi.firstChild == null) return true
        // string literals could be identifiers of calls e.g. `"main" {}` in gradle.kts
        if (sourcePsi is KtStringTemplateExpression && sourcePsi.parent is KtCallExpression) return true
        return false
    }

    override fun computeParent(): UElement? {
        val parent = sourcePsi?.parent ?: return null

        return if (parent is KDocName && parent.getQualifier() != null) // e.g. for UElement in org.jetbrains.uast.UElement
            createKDocNameSimpleNameReference(parentKDocName = parent, givenParent = null)
        else
            getIdentifierParentForCall(parent) ?: parent.toUElement()
    }

    private fun getIdentifierParentForCall(parent: PsiElement): UElement? {
        val parentParent = parent.parent
        if (parentParent is KtCallElement && parentParent.calleeExpression == parent) { // method identifiers in calls
            return parentParent.toUElement()
        }

        return generateSequence(parent, parentsSequence)
            .take(3)
            .mapNotNull(findConstructorCalleeMapper)
            .mapNotNull(superCallOrAnnotationMapper)
            .firstOrNull()
    }

    constructor(javaPsi: PsiElement?, sourcePsi: PsiElement?, uastParent: UElement?) : this({ javaPsi }, sourcePsi, uastParent)
    constructor(sourcePsi: PsiElement?, uastParent: UElement?) : this({ null }, sourcePsi, uastParent)

    private companion object {
        val parentsSequence: (PsiElement) -> PsiElement? = { it.parent }
        val findConstructorCalleeMapper: (PsiElement) -> KtConstructorCalleeExpression? = {
            (it as? KtTypeReference)?.parentAs<KtConstructorCalleeExpression>()
        }
        val superCallOrAnnotationMapper: (KtConstructorCalleeExpression) -> UElement? = {
            val entry = it.parentAs<KtSuperTypeCallEntry>()
            if (entry != null)
                (entry.getParentObjectLiteralExpression()?.toUElement() as? KotlinUObjectLiteralExpression)?.constructorCall
                    ?: entry.toUElement()
            else
                it.parentAs<KtAnnotationEntry>()?.toUElement()
        }
    }
}
