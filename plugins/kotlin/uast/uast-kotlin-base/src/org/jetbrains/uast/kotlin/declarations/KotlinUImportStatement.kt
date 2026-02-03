// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedElementSelector
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UImportStatement
import org.jetbrains.uast.UNINITIALIZED_UAST_PART
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.kotlin.internal.DelegatedMultiResolve

@ApiStatus.Internal
class KotlinUImportStatement(
    override val psi: KtImportDirective,
    givenParent: UElement?
) : KotlinAbstractUElement(givenParent), UImportStatement, DelegatedMultiResolve {

    private var importRefPart: Any? = UNINITIALIZED_UAST_PART

    override val javaPsi: PsiElement? = null

    override val sourcePsi: KtImportDirective = psi

    override val isOnDemand: Boolean = sourcePsi.isAllUnder

    private val importRef: ImportReference?
        get() {
            if (importRefPart == UNINITIALIZED_UAST_PART) {
                importRefPart = sourcePsi.importedReference?.let {
                    ImportReference(it, this)
                }
            }
            return importRefPart as ImportReference?
        }

    override val importReference: UElement? = importRef

    override fun resolve(): PsiElement? = importRef?.resolve()

    private class ImportReference(
        override val psi: KtExpression,
        givenParent: UElement?
    ) : KotlinAbstractUExpression(givenParent), USimpleNameReferenceExpression {
        override val sourcePsi: KtExpression = psi

        override val identifier: String = psi.text

        override val resolvedName: String = psi.text

        override fun resolve(): PsiElement? {
            val reference = sourcePsi.getQualifiedElementSelector() as? KtReferenceExpression ?: return null
            return baseResolveProviderService.resolveToDeclaration(reference)
        }
    }
}
