// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedElementSelector
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UImportStatement
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.kotlin.internal.DelegatedMultiResolve

class KotlinUImportStatement(
        override val psi: KtImportDirective,
        givenParent: UElement?
) : KotlinAbstractUElement(givenParent), UImportStatement, DelegatedMultiResolve {

    override val javaPsi = null

    override val sourcePsi = psi

    override val isOnDemand: Boolean
        get() = psi.isAllUnder

    private val importRef by lz {
        psi.importedReference?.let {
            ImportReference(it, psi.name ?: psi.text, this, psi)
        }
    }
    
    override val importReference: UElement?
        get() = importRef

    override fun resolve() = importRef?.resolve()

    private class ImportReference(
            override val psi: KtExpression,
            override val identifier: String,
            givenParent: UElement?,
            private val importDirective: KtImportDirective
    ) : KotlinAbstractUExpression(givenParent), USimpleNameReferenceExpression {
        override val resolvedName: String?
            get() = identifier

        override fun asRenderString(): String = importDirective.importedFqName?.asString() ?: psi.text

        override fun resolve(): PsiElement? {
            val reference = psi.getQualifiedElementSelector() as? KtReferenceExpression ?: return null
            return resolveToDeclaration(reference)
        }
    }
}