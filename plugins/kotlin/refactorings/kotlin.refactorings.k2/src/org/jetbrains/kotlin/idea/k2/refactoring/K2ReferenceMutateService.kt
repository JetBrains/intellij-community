// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring

import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import com.intellij.util.IncorrectOperationException
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.KtSymbolBasedReference
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.KtSyntheticJavaPropertySymbol
import org.jetbrains.kotlin.idea.base.analysis.api.utils.invokeShortening
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinOptimizeImportsFacility
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.kdoc.KDocElementFactory
import org.jetbrains.kotlin.idea.refactoring.rename.KtReferenceMutateServiceBase
import org.jetbrains.kotlin.idea.references.KDocReference
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference
import org.jetbrains.kotlin.idea.references.KtSimpleReference
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getPossiblyQualifiedCallExpression
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedElement

/**
 * At the moment, this implementation of [org.jetbrains.kotlin.idea.references.KtReferenceMutateService] is not able to do some of the
 * required operations. It is OK and on purpose - this functionality will be added later.
 */
@Suppress("UNCHECKED_CAST")
internal class K2ReferenceMutateService : KtReferenceMutateServiceBase() {
    override fun bindToElement(ktReference: KtReference, element: PsiElement): PsiElement = when (ktReference) {
        is KtSimpleNameReference -> bindToElement(ktReference, element, KtSimpleNameReference.ShorteningMode.FORCED_SHORTENING)
        is KDocReference -> bindToElement(ktReference, element, KtSimpleNameReference.ShorteningMode.FORCED_SHORTENING)
        else -> throw IncorrectOperationException()
    }

    private fun KtFile.unusedImports(): Set<KtImportDirective> =
        KotlinOptimizeImportsFacility.getInstance().analyzeImports(this)?.unusedImports?.toSet().orEmpty()

    @OptIn(KtAllowAnalysisFromWriteAction::class, KtAllowAnalysisOnEdt::class)
    private fun <R : KtElement> KtFile.withOptimizedImports(shorten: Boolean, replacement: () -> R?): PsiElement? {
        allowAnalysisFromWriteAction {
            allowAnalysisOnEdt {
                val unusedImportsBefore = unusedImports()
                val newElement = replacement() ?: return null
                val unusedImportsAfter = unusedImports()
                val importsToRemove =  unusedImportsAfter - unusedImportsBefore
                importsToRemove.forEach(PsiElement::delete)
                return if (shorten) {
                    // TODO Reference shortening should be done BEFORE removing the obsolete imports
                    // (but currently it is blocked by KTIJ-28004 and KTIJ-27841)
                    val newShortenings = analyze(newElement) {
                        collectPossibleReferenceShorteningsInElement(newElement)
                    }
                    newShortenings.invokeShortening().firstOrNull() ?: newElement
                } else newElement
            }
        }
    }

    @RequiresWriteLock
    private fun bindToElement(
        docReference: KDocReference,
        targetElement: PsiElement,
        shorteningMode: KtSimpleNameReference.ShorteningMode
    ): PsiElement {
        val docElement = docReference.element
        val targetFqn = targetElement.kotlinFqName ?: return docElement
        if (targetFqn.isRoot) return docElement
        val shorten = shorteningMode != KtSimpleNameReference.ShorteningMode.NO_SHORTENING
        return docElement.containingKtFile.withOptimizedImports(shorten) {
            val newDocReference = KDocElementFactory(targetElement.project).createNameFromText(targetFqn.asString())
            docReference.expression.replaced(newDocReference)
        } ?: docElement
    }

    @RequiresWriteLock
    override fun bindToFqName(
        simpleNameReference: KtSimpleNameReference,
        fqName: FqName,
        shorteningMode: KtSimpleNameReference.ShorteningMode, // delayed shortening is not supported
        targetElement: PsiElement?
    ): PsiElement {
        val expression = simpleNameReference.expression
        if (fqName.isRoot) return expression
        val parent = expression.parent
        val writableFqn = if (parent is KtCallableReferenceExpression && parent.callableReference == expression) {
            FqName.topLevel(fqName.shortName())
        } else if (fqName.pathSegments().last().asString() == "Companion") {
            fqName.parent()
        } else {
            fqName
        }
        val importDirective = expression.parentOfType<KtImportDirective>(withSelf = false)
        val shorten = shorteningMode != KtSimpleNameReference.ShorteningMode.NO_SHORTENING && importDirective == null
        val newElement = expression.containingKtFile.withOptimizedImports(shorten) {
            val element = when (val elementToReplace = expression.getQualifiedElement()) {
                is KtUserType -> elementToReplace.replaceWith(writableFqn)
                is KtDotQualifiedExpression -> elementToReplace.replaceWith(writableFqn)
                is KtCallExpression -> elementToReplace.replaceWith(writableFqn)
                is KtSimpleNameExpression -> elementToReplace.replaceWith(writableFqn)
                else -> null
            }
            element
        } ?: expression
        return newElement
    }

    private fun KtUserType.replaceWith(fqName: FqName): KtUserType {
        return if (qualifier == null) {
            val typeArgText = typeArgumentList?.text ?: ""
            val newReference = KtPsiFactory(project).createType(fqName.asString() + typeArgText).typeElement as? KtUserType
                ?: error("Could not create type from $fqName")
            replaced(newReference)
        } else {
            val parentFqn = fqName.parent()
            if (parentFqn.isRoot) {
                deleteQualifier()
            }  else {
                qualifier?.replaceWith(parentFqn) // do recursive short name replacement to preserve type arguments
            }
            referenceExpression?.replaceShortName(fqName)?.parent as KtUserType
        }
    }

    private fun KtDotQualifiedExpression.replaceWith(fqName: FqName): KtExpression? {
        val selectorExpression = selectorExpression ?: return null
        val newSelectorExpression = when (selectorExpression) {
            is KtSimpleNameExpression -> selectorExpression.replaceShortName(fqName)
            is KtCallExpression -> selectorExpression.replaceShortName(fqName)
            else -> null
        } ?: return null
        return replaceWithQualified(fqName, newSelectorExpression)
    }

    private fun KtExpression.replaceWithQualified(fqName: FqName, selectorExpression: KtExpression): KtExpression {
        val parentFqName = fqName.parent()
        if (parentFqName.isRoot) return selectorExpression
        val packageName = fqName.parent().asString()
        val newQualifiedExpression = KtPsiFactory(project).createExpression("$packageName.${selectorExpression.text}")
        return replaced(newQualifiedExpression)
    }

    private fun KtCallExpression.replaceWith(fqName: FqName): KtExpression {
        val newCall = replaceShortName(fqName)
        return newCall.replaceWithQualified(fqName, newCall)
    }

    private fun KtCallExpression.replaceShortName(fqName: FqName): KtExpression {
        val psiFactory = KtPsiFactory(project)
        val newName = psiFactory.createSimpleName(fqName.shortName().asString())
        calleeExpression?.replace(newName)
        return this
    }

    private fun KtSimpleNameExpression.replaceWith(fqName: FqName): KtExpression {
        val newNameExpr = replaceShortName(fqName)
        return newNameExpr.replaceWithQualified(fqName, newNameExpr)
    }

    private fun KtSimpleNameExpression.replaceShortName(fqName: FqName): KtExpression {
        val newNameExpression = KtPsiFactory(project).createSimpleName(fqName.shortName().asString())
        return replaced(newNameExpression)
    }

    override fun KtSimpleReference<KtNameReferenceExpression>.suggestVariableName(
        expr: KtExpression,
        context: PsiElement): String {
        @OptIn(KtAllowAnalysisOnEdt::class)
        allowAnalysisOnEdt {
            analyze(expr) {
                return KotlinNameSuggester(KotlinNameSuggester.Case.CAMEL).suggestExpressionNames(expr).first()
            }
        }
    }

    override fun handleElementRename(ktReference: KtReference, newElementName: String): PsiElement? {
        @OptIn(KtAllowAnalysisFromWriteAction::class)
        allowAnalysisFromWriteAction {
            if (ktReference is KtSymbolBasedReference) {
                @OptIn(KtAllowAnalysisOnEdt::class)
                allowAnalysisOnEdt {
                    analyze(ktReference.element) {
                        val symbol = ktReference.resolveToSymbol()
                        if (symbol is KtSyntheticJavaPropertySymbol) {
                            val newName = (ktReference as? KtSimpleReference<KtNameReferenceExpression>)?.getAdjustedNewName(newElementName)
                            if (newName == null) {
                                return (ktReference as? KtSimpleReference<KtNameReferenceExpression>)?.renameToOrdinaryMethod(newElementName)
                            } else {
                                return super.handleElementRename(ktReference, newName.asString())
                            }
                        }
                    }
                }
            }

            return super.handleElementRename(ktReference, newElementName)
        }
    }

    override fun canMoveLambdaOutsideParentheses(newExpression: KtDotQualifiedExpression): Boolean {
        return newExpression.getPossiblyQualifiedCallExpression()?.canMoveLambdaOutsideParentheses() == true
    }

}