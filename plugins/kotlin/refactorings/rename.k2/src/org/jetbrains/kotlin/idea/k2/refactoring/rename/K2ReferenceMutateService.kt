// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.rename

import com.intellij.psi.PsiElement
import com.intellij.util.IncorrectOperationException
import org.jetbrains.kotlin.idea.refactoring.rename.KtReferenceMutateServiceBase
import org.jetbrains.kotlin.idea.references.*
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*

/**
 * At the moment, this implementation of [KtReferenceMutateService] is not able to do any of required operations. It is OK and
 * on purpose - this functionality will be added later.
 */
internal class K2ReferenceMutateService : KtReferenceMutateServiceBase() {
    override fun bindToElement(
      simpleNameReference: KtSimpleNameReference,
      element: PsiElement,
      shorteningMode: KtSimpleNameReference.ShorteningMode
    ): PsiElement {
        operationNotSupportedInK2Error()
    }

    override fun bindToElement(ktReference: KtReference, element: PsiElement): PsiElement {
        operationNotSupportedInK2Error()
    }

    override fun bindToFqName(
      simpleNameReference: KtSimpleNameReference,
      fqName: FqName,
      shorteningMode: KtSimpleNameReference.ShorteningMode,
      targetElement: PsiElement?
    ): PsiElement {
        operationNotSupportedInK2Error()
    }

    override fun KtArrayAccessReference.renameTo(newElementName: String): KtExpression {
        operationNotSupportedInK2Error()
    }

    override fun KDocReference.renameTo(newElementName: String): PsiElement? {
        operationNotSupportedInK2Error()
    }

    override fun KtInvokeFunctionReference.renameTo(newElementName: String): PsiElement {
        operationNotSupportedInK2Error()
    }

    override fun SyntheticPropertyAccessorReference.renameTo(newElementName: String): KtElement? {
        operationNotSupportedInK2Error()
    }

    override fun KtDefaultAnnotationArgumentReference.renameTo(newElementName: String): KtValueArgument {
        operationNotSupportedInK2Error()
    }

    override fun convertOperatorToFunctionCall(opExpression: KtOperationExpression): Pair<KtExpression, KtSimpleNameExpression> {
        operationNotSupportedInK2Error()
    }

    private fun operationNotSupportedInK2Error(): Nothing {
        throw IncorrectOperationException("K2 plugin does not yet support this operation")
    }
}