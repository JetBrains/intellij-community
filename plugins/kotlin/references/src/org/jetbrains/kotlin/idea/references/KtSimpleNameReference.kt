// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.references

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtEnumEntrySuperclassReferenceExpression
import org.jetbrains.kotlin.psi.KtImplementationDetail
import org.jetbrains.kotlin.psi.KtImportAlias
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtWhenConditionInRange
import org.jetbrains.kotlin.psi.psiUtil.getParentOfTypeAndBranch
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.utils.exceptions.rethrowExceptionWithDetails
import org.jetbrains.kotlin.utils.exceptions.withPsiEntry

@SubclassOptInRequired(KtImplementationDetail::class)
abstract class KtSimpleNameReference(
    expression: KtSimpleNameExpression,
) : KtSimpleReference<KtSimpleNameExpression>(expression) {
    // Extension point used by deprecated android extensions.
    abstract fun isReferenceToViaExtension(element: PsiElement): Boolean

    override fun isReferenceTo(candidateTarget: PsiElement): Boolean {
        if (!canBeReferenceTo(candidateTarget)) return false
        if (isReferenceToViaExtension(candidateTarget)) return true
        return super.isReferenceTo(candidateTarget)
    }

    override fun getRangeInElement(): TextRange {
        if (element is KtEnumEntrySuperclassReferenceExpression) {
            // `KtEnumEntrySuperclassReferenceExpression` does not have a proper name element inside of it;
            // instead, `getReferencedNameElement` returns the parent enum class for it.
            // Since `getRangeInElement` is expected to return the range somewhere inside of the `element`,
            // the only reasonable option is to return the whole text range of the `element` in this case.
            return element.textRangeInParent
        }

        val referencedElement = element.getReferencedNameElement()
        val startOffset = element.startOffset

        return try {
            referencedElement.textRange.shiftLeft(startOffset)
        } catch (e: IllegalArgumentException) {
            rethrowExceptionWithDetails(
                "Could not compute 'getRangeInElement' for element of class '${element.javaClass}'",
                e,
            ) {
                withPsiEntry("element", element)
                withPsiEntry("referencedElement", referencedElement)
            }
        }
    }

    override fun canRename(): Boolean {
        if (expression.getParentOfTypeAndBranch<KtWhenConditionInRange>(strict = true) { operationReference } != null) return false

        val elementType = expression.getReferencedNameElementType()
        if (elementType == KtTokens.PLUSPLUS || elementType == KtTokens.MINUSMINUS) return false

        return true
    }

    enum class ShorteningMode {
        NO_SHORTENING,
        DELAYED_SHORTENING,
        FORCED_SHORTENING
    }

    fun bindToElement(element: PsiElement, shorteningMode: ShorteningMode = ShorteningMode.DELAYED_SHORTENING): PsiElement =
        getKtReferenceMutateService().bindToElement(this, element, shorteningMode)

    fun bindToFqName(
        fqName: FqName,
        shorteningMode: ShorteningMode = ShorteningMode.DELAYED_SHORTENING,
        targetElement: PsiElement? = null
    ): PsiElement =
        getKtReferenceMutateService().bindToFqName(this, fqName, shorteningMode, targetElement)

    abstract fun getImportAlias(): KtImportAlias?
}
