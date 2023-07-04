// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring

import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import com.intellij.util.IncorrectOperationException
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.base.analysis.api.utils.invokeShortening
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.refactoring.intentions.OperatorToFunctionConverter
import org.jetbrains.kotlin.idea.refactoring.rename.KtReferenceMutateServiceBase
import org.jetbrains.kotlin.idea.references.KtDefaultAnnotationArgumentReference
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference
import org.jetbrains.kotlin.idea.references.SyntheticPropertyAccessorReference
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*

/**
 * At the moment, this implementation of [org.jetbrains.kotlin.idea.references.KtReferenceMutateService] is not able to do some of the
 * required operations. It is OK and on purpose - this functionality will be added later.
 */
internal class K2ReferenceMutateService : KtReferenceMutateServiceBase() {
    @RequiresWriteLock
    override fun bindToFqName(
        simpleNameReference: KtSimpleNameReference,
        fqName: FqName,
        shorteningMode: KtSimpleNameReference.ShorteningMode, // not supported in K2, it always does FORCED_SHORTENING
        targetElement: PsiElement?
    ): PsiElement {
        if (targetElement !is KtElement) operationNotSupportedInK2Error() // TODO fix reference shortener for non-Kotlin target elements
        val expression = simpleNameReference.expression
        if (fqName.isRoot) return expression

        val containingFile = expression.containingKtFile
        val unusedImportsBeforeChange = analyze(containingFile) { // TODO improve this
            analyseImports(containingFile)
        }.unusedImports

        val anchorElement = expression.parentOfType<KtUserType>(withSelf = false)
                            ?: expression.parentOfType<KtDotQualifiedExpression>(withSelf = false)
                            ?: expression
        val newElement = when (anchorElement) {
            is KtUserType -> anchorElement.replaceWith(fqName)
            is KtSimpleNameExpression -> anchorElement.replaceWith(fqName)
            is KtDotQualifiedExpression -> anchorElement.replaceWith(fqName)
            else -> null
        } ?: return expression

        val unusedImportsAfterChange = analyze(containingFile) {
            analyseImports(containingFile)
        }.unusedImports
        val importsToRemove = unusedImportsAfterChange - unusedImportsBeforeChange
        importsToRemove.forEach {
            it.delete()
        }

        val newShortenings = analyze(newElement) { collectPossibleReferenceShorteningsInElement(newElement) }
        return newShortenings.invokeShortening().firstOrNull() ?: newElement
    }

    private fun KtTypeElement.replaceWith(fqName: FqName): KtTypeElement {
        val newReference = KtPsiFactory(project).createType(fqName.asString()).typeElement
                           ?: error("Could not create type from $fqName")
        return replaced(newReference)
    }

    private fun KtSimpleNameExpression.replaceWith(fqName: FqName): KtExpression {
        val newNameExpression = KtPsiFactory(project).createExpression(fqName.asString())
        return replaced(newNameExpression)
    }

    private fun KtDotQualifiedExpression.replaceWith(fqName: FqName): KtExpression? {
        val psiFactory = KtPsiFactory(project)
        val selectorExpression = selectorExpression ?: return null
        val newExpression = when (selectorExpression) {
            is KtNameReferenceExpression -> {
                psiFactory.createExpression(fqName.asString())
            }
            is KtCallExpression -> {
                val newName = psiFactory.createSimpleName(fqName.shortName().asString())
                selectorExpression.calleeExpression?.replace(newName)
                val packageName = fqName.parent().asString()
                psiFactory.createExpression("$packageName.${selectorExpression.text}")
            }
            else -> return null
        }
        return replaced(newExpression)
    }

    override fun SyntheticPropertyAccessorReference.renameTo(newElementName: String): KtElement? {
        operationNotSupportedInK2Error()
    }

    override fun KtDefaultAnnotationArgumentReference.renameTo(newElementName: String): KtValueArgument {
        operationNotSupportedInK2Error()
    }

    override fun replaceWithImplicitInvokeInvocation(newExpression: KtDotQualifiedExpression): KtExpression? =
      OperatorToFunctionConverter.replaceExplicitInvokeCallWithImplicit(newExpression)

    private fun operationNotSupportedInK2Error(): Nothing {
        throw IncorrectOperationException("K2 plugin does not yet support this operation")
    }
}