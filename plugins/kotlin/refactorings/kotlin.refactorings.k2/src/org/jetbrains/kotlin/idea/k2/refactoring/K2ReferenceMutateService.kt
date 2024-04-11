// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.jetbrains.kotlin.analysis.api.types.KtFunctionalType
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.psi.imports.addImport
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.util.quoteIfNeeded
import org.jetbrains.kotlin.idea.kdoc.KDocElementFactory
import org.jetbrains.kotlin.idea.refactoring.rename.KtReferenceMutateServiceBase
import org.jetbrains.kotlin.idea.references.KDocReference
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference
import org.jetbrains.kotlin.idea.references.KtSimpleReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getPossiblyQualifiedCallExpression
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedElementOrCallableRef
import org.jetbrains.kotlin.psi.psiUtil.isExtensionDeclaration
import org.jetbrains.kotlin.psi.psiUtil.isTopLevelKtOrJavaMember

/**
 * At the moment, this implementation of [org.jetbrains.kotlin.idea.references.KtReferenceMutateService] is not able to do some of the
 * required operations. It is OK and on purpose - this functionality will be added later.
 */
@Suppress("UNCHECKED_CAST")
internal class K2ReferenceMutateService : KtReferenceMutateServiceBase() {
    @OptIn(KtAllowAnalysisFromWriteAction::class, KtAllowAnalysisOnEdt::class)
    override fun bindToElement(ktReference: KtReference, element: PsiElement): PsiElement = allowAnalysisOnEdt {
        return allowAnalysisFromWriteAction {
            when (ktReference) {
                is KtSimpleNameReference -> bindToElement(ktReference, element, KtSimpleNameReference.ShorteningMode.FORCED_SHORTENING)
                is KDocReference -> bindToElement(ktReference, element, KtSimpleNameReference.ShorteningMode.FORCED_SHORTENING)
                else -> throw IncorrectOperationException()
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
        if (docReference.isReferenceTo(targetElement)) return docElement
        val targetFqn = targetElement.kotlinFqName ?: return docElement
        if (targetFqn.isRoot) return docElement
        val replacedDocReference = modifyPsiWithOptimizedImports(docElement.containingKtFile) {
            val newDocReference = KDocElementFactory(targetElement.project).createNameFromText(targetFqn.asString())
            docElement.replaced(newDocReference)
        }
        return if (shorteningMode != KtSimpleNameReference.ShorteningMode.NO_SHORTENING) {
            shortenReferences(replacedDocReference) ?: replacedDocReference
        } else replacedDocReference
    }

    private class ReplaceResult(val replacedElement: KtElement, val canBeShortened: Boolean = true)

    @OptIn(KtAllowAnalysisOnEdt::class, KtAllowAnalysisFromWriteAction::class)
    @RequiresWriteLock
    override fun bindToFqName(
        simpleNameReference: KtSimpleNameReference,
        fqName: FqName,
        shorteningMode: KtSimpleNameReference.ShorteningMode, // delayed shortening is not supported
        targetElement: PsiElement?
    ): PsiElement = allowAnalysisOnEdt {
        allowAnalysisFromWriteAction {
            val expression = simpleNameReference.expression
            if (targetElement != null) { // if we are already referencing the target, there is no need to call bindToElement
                if (simpleNameReference.isReferenceTo(targetElement)) return expression
            } else {
                // Here we assume that the passed fqName uniquely identifiers the new target element
                val oldTarget = simpleNameReference.resolve()
                if (oldTarget?.kotlinFqName == fqName) return expression
            }
            if (fqName.isRoot) return expression
            val writableFqn = if (fqName.pathSegments().last().asString() == SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT.asString()) {
                fqName.parent()
            } else {
                fqName
            }
            val elementToReplace = expression.getQualifiedElementOrCallableRef()
            val result = modifyPsiWithOptimizedImports(expression.containingKtFile) {
                when (elementToReplace) {
                    is KtUserType -> elementToReplace.replaceWith(writableFqn, targetElement)
                    is KtQualifiedExpression -> elementToReplace.replaceWith(writableFqn, targetElement)
                    is KtCallExpression -> elementToReplace.replaceWith(writableFqn, targetElement)
                    is KtCallableReferenceExpression -> elementToReplace.replaceWith(writableFqn, targetElement)
                    is KtSimpleNameExpression -> elementToReplace.replaceWith(writableFqn, targetElement)
                    else -> null
                }
            } ?: return expression
            val shouldShorten = shorteningMode != KtSimpleNameReference.ShorteningMode.NO_SHORTENING && result.canBeShortened
            val shortenResult = if (shouldShorten) {
                shortenReferences(result.replacedElement) ?: result.replacedElement
            } else result.replacedElement
            shortenResult
        }
    }

    private fun KtUserType.replaceWith(fqName: FqName, targetElement: PsiElement?): ReplaceResult {
        val replacedElement = if (qualifier == null) {
            val typeArgText = typeArgumentList?.text ?: ""
            val newReference = KtPsiFactory(project).createType(fqName.asString() + typeArgText).typeElement as? KtUserType
                ?: error("Could not create type from $fqName")
            replaced(newReference)
        } else {
            val parentFqn = fqName.parent()
            if (parentFqn.isRoot) {
                deleteQualifier()
            } else {
                qualifier?.replaceWith(parentFqn, targetElement) // do recursive short name replacement to preserve type arguments
            }
            referenceExpression?.replaceShortName(fqName, targetElement)?.parent as KtUserType
        }
        return ReplaceResult(replacedElement)
    }

    private fun PsiElement.isCallableAsExtensionFunction(): Boolean {
        if (isExtensionDeclaration()) return true
        return if (this is KtProperty) {
            analyze(this) {
                val returnType = getReturnKtType()
                returnType is KtFunctionalType && returnType.receiverType != null
            }
        } else false
    }

    private fun KtQualifiedExpression.replaceWith(fqName: FqName, targetElement: PsiElement?): ReplaceResult? {
        val isPartOfImport = parentOfType<KtImportDirective>(withSelf = false) != null
        val selectorExpression = selectorExpression ?: return null
        val newSelectorExpression = when (selectorExpression) {
            is KtSimpleNameExpression -> {
                val newShortName = selectorExpression.replaceShortName(fqName, targetElement)
                if (targetElement?.isCallableAsExtensionFunction() == true && !isPartOfImport) {
                    containingKtFile.addImport(fqName)
                    return ReplaceResult(newShortName, false)
                } else newShortName
            }

            is KtCallExpression -> {
                val newCall = selectorExpression.replaceShortName(fqName)
                if (targetElement?.isCallableAsExtensionFunction() == true) {
                    containingKtFile.addImport(fqName)
                    return ReplaceResult(newCall, false)
                } else newCall
            }

            else -> null
        } ?: return null
        return ReplaceResult(replaceWithQualified(fqName, newSelectorExpression), !isPartOfImport)
    }

    private fun KtExpression.replaceWithQualified(fqName: FqName, selectorExpression: KtExpression): KtExpression {
        val parentFqName = fqName.parent()
        if (parentFqName.isRoot) return replaced(selectorExpression)
        val packageName = fqName.parent().quoteIfNeeded().asString()
        val newQualifiedExpression = KtPsiFactory(project).createExpression("$packageName.${selectorExpression.text}")
        return replaced(newQualifiedExpression)
    }

    private fun KtCallExpression.replaceWith(fqName: FqName, targetElement: PsiElement?): ReplaceResult {
        val newCall = replaceShortName(fqName)
        if (targetElement?.isCallableAsExtensionFunction() == true) {
            containingKtFile.addImport(fqName)
            return ReplaceResult(newCall, false)
        }
        return ReplaceResult(newCall.replaceWithQualified(fqName, newCall), true)
    }

    private fun KtCallableReferenceExpression.replaceWith(fqName: FqName, targetElement: PsiElement?): ReplaceResult? {
        if (targetElement == null) return null
        val (callableReference, shouldShorten) = if (targetElement.isTopLevelKtOrJavaMember()) {
            containingKtFile.addImport(fqName)
            KtPsiFactory(project).createCallableReferenceExpression("::${fqName.shortName()}") to false
        } else {
            KtPsiFactory(project).createCallableReferenceExpression("${fqName.parent().asString()}::${fqName.shortName()}") to true
        }
        if (callableReference == null) return null
        return ReplaceResult(replaced(callableReference), shouldShorten)
    }

    private fun KtCallExpression.replaceShortName(fqName: FqName): KtExpression {
        val psiFactory = KtPsiFactory(project)
        val newName = psiFactory.createSimpleName(fqName.shortName().asString())
        calleeExpression?.replace(newName)
        return this
    }

    private fun KtSimpleNameExpression.replaceWith(fqName: FqName, targetElement: PsiElement?): ReplaceResult {
        val newNameExpr = replaceShortName(fqName, targetElement)
        return if (targetElement?.isCallableAsExtensionFunction() == true) { // operation references (infix function calls)
            newNameExpr.containingKtFile.addImport(fqName)
            ReplaceResult(newNameExpr, false)
        } else {
            ReplaceResult(newNameExpr.replaceWithQualified(fqName, newNameExpr))
        }
    }

    private fun KtSimpleNameExpression.replaceShortName(fqName: FqName, targetElement: PsiElement?): KtExpression {
        val psiFactory = KtPsiFactory(project)
        val shortName = fqName.shortName().asString()
        val isInfixFun = targetElement is KtNamedFunction && targetElement.modifierList?.hasModifier(KtTokens.INFIX_KEYWORD) == true
        val newNameExpression = if (isInfixFun) psiFactory.createOperationName(shortName) else psiFactory.createSimpleName(shortName)
        return replaced(newNameExpression)
    }

    override fun KtSimpleReference<KtNameReferenceExpression>.suggestVariableName(
        expr: KtExpression,
        context: PsiElement
    ): String {
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