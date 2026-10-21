// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.refactoring.inline.codeInliner

import org.jetbrains.kotlin.analysis.api.KaImplementationDetail
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.scopeContext
import org.jetbrains.kotlin.analysis.api.expressions.expressionType
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.renderer.render
import org.jetbrains.kotlin.analysis.api.renderer.types.KaExpandedTypeRenderingMode
import org.jetbrains.kotlin.analysis.api.renderer.types.KaTypeRenderer
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.session.KaSessionProvider
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.containingSymbol
import org.jetbrains.kotlin.analysis.api.symbols.importableFqName
import org.jetbrains.kotlin.analysis.api.symbols.symbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.expandedSymbol
import org.jetbrains.kotlin.analysis.api.types.fullyExpandedType
import org.jetbrains.kotlin.analysis.api.types.isFunctionType
import org.jetbrains.kotlin.analysis.api.types.isSubtypeOf
import org.jetbrains.kotlin.analysis.api.types.isSuspendFunctionType
import org.jetbrains.kotlin.analysis.api.types.type
import org.jetbrains.kotlin.analysis.utils.printer.PrettyPrinter
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.UsageReplacementStrategy
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCallableReferenceExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunctionTypeReceiver
import org.jetbrains.kotlin.psi.KtNullableType
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtTypeAlias
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getParentOfTypeAndBranch
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector
import org.jetbrains.kotlin.types.Variance

class TypeAliasUsageReplacementStrategy(val typeAlias: KtTypeAlias) : UsageReplacementStrategy {

    @OptIn(KaAllowAnalysisOnEdt::class)
    fun hasUnreplaceableInnerConstructor(usage: KtReferenceExpression): Boolean = allowAnalysisOnEdt {
        analyze(usage) {
            val callElement = usage.parent as? KtCallElement ?: return@analyze false
            if (callElement.getQualifiedExpressionForSelector() != null) return@analyze false
            val classSymbol = innerClassSymbol(callElement) ?: return@analyze false
            !shortNameResolvesTo(usage, classSymbol) && innerReceiverQualifier(usage, classSymbol) == null
        }
    }

    context(_: KaSession)
    private fun innerClassSymbol(callElement: KtCallElement): KaNamedClassSymbol? =
        ((callElement as? KtExpression)?.expressionType?.expandedSymbol as? KaNamedClassSymbol)?.takeIf { it.isInner }

    context(_: KaSession)
    private fun shortNameResolvesTo(usage: KtReferenceExpression, classSymbol: KaNamedClassSymbol): Boolean {
        val scopes = usage.containingKtFile.scopeContext(usage).scopes
        val visibleClass = scopes.asSequence()
            .flatMap { it.scope.classifiers(classSymbol.name) }
            .firstOrNull()
        return visibleClass == classSymbol && scopes.none { it.scope.callables { name -> name == classSymbol.name }.any() }
    }

    context(_: KaSession)
    private fun innerReceiverQualifier(usage: KtReferenceExpression, classSymbol: KaNamedClassSymbol): String? {
        val outerClass = classSymbol.containingSymbol as? KaClassSymbol ?: return null
        val receivers = usage.containingKtFile.scopeContext(usage).implicitReceivers
        val receiverIndex = receivers.indexOfFirst { it.type.isSubtypeOf(outerClass) }
        if (receiverIndex < 0) return null
        if (receiverIndex == 0) return "this"
        return receivers[receiverIndex].label?.let { "this@$it" }
    }

    @OptIn(KaAllowAnalysisFromWriteAction::class, KaAllowAnalysisOnEdt::class) //under potemkin progress
    override fun createReplacer(usage: KtReferenceExpression): (() -> KtElement?)? {
        val refElement = usage.getParentOfTypeAndBranch<KtUserType> { referenceExpression }
            ?: usage.getNonStrictParentOfType<KtSimpleNameExpression>() ?: return null

        return when (refElement) {
            is KtUserType -> {
                {
                    allowAnalysisOnEdt {
                        allowAnalysisFromWriteAction {
                            inlineIntoType(refElement)
                        }
                    }
                }
            }

            else -> {
                {
                    allowAnalysisOnEdt {
                        allowAnalysisFromWriteAction {
                            inlineIntoCall(refElement as KtReferenceExpression)
                        }
                    }
                }
            }
        }
    }

    @OptIn(KaImplementationDetail::class)
    fun inlineIntoCall(usage: KtReferenceExpression): KtElement? {
        val callableReference = usage.parent as? KtCallableReferenceExpression
        if (callableReference != null) {
            val (fqName, expandedClassPsi) = analyze(typeAlias) {
                val expandedClass = typeAlias.symbol.expandedType.expandedSymbol ?: return null
                (expandedClass.importableFqName ?: return null) to expandedClass.psi
            }
            if (callableReference.callableReference == usage) {
                return (usage.mainReference as KtSimpleNameReference)
                    .bindToFqName(fqName, targetElement = expandedClassPsi) as? KtElement
            }
            val receiverTypeText = analyze(callableReference) {
                (callableReference.expressionType as? KaClassType)
                    ?.typeArguments?.firstOrNull()
                    ?.type?.fullyExpandedType?.takeIf { it.expandedSymbol?.importableFqName == fqName }
                    ?.render(expandAliasRenderer(), position = Variance.INVARIANT)
            } ?: fqName.asString()
            val replacement = KtPsiFactory(typeAlias.project)
                .createExpression("$receiverTypeText::${callableReference.callableReference.text}")
            return callableReference.replaced(replacement).apply { shortenReferences(this) }
        }
        analyze(usage) {
            val psiFactory = KtPsiFactory(typeAlias.project)

            val callElement = usage.parent as? KtCallExpression ?: usage
            val expandedType = callElement.expressionType?.fullyExpandedType ?: return null
            val expandedTypeArgumentsText = if (callElement is KtCallExpression && expandedType is KaClassType) {
                val aliasTypeRenderer = expandAliasRenderer()
                val session = KaSessionProvider.getInstance(callElement.project).getAnalysisSession(callElement)
                expandedType.typeArguments
                    .takeIf { it.isNotEmpty() }
                    ?.joinToString(prefix = "<", postfix = ">") {
                        val printer = PrettyPrinter()
                        aliasTypeRenderer.typeProjectionRenderer
                            .renderTypeProjection(session, it, aliasTypeRenderer, printer)
                        printer.toString()
                    }
            } else {
                null
            }
            val expandedClassSymbol = expandedType.expandedSymbol
            if (expandedClassSymbol is KaNamedClassSymbol && expandedClassSymbol.isInner) {
                val innerClassName = expandedClassSymbol.name.asString()
                val qualifiedCall = callElement.getQualifiedExpressionForSelector()
                if (qualifiedCall != null || shortNameResolvesTo(usage, expandedClassSymbol)) {
                    specifyTypeArguments(callElement, psiFactory, expandedTypeArgumentsText)
                    usage.replaced(psiFactory.createSimpleName(innerClassName))
                    return callElement
                }

                val receiverQualifier = innerReceiverQualifier(usage, expandedClassSymbol)
                if (receiverQualifier != null) {
                    specifyTypeArguments(callElement, psiFactory, expandedTypeArgumentsText)
                    usage.replaced(psiFactory.createSimpleName(innerClassName))
                    return callElement.replaced(psiFactory.createExpression("$receiverQualifier.${callElement.text}"))
                }
            }
            val expandedTypeFqName = expandedClassSymbol?.importableFqName ?: return null
            specifyTypeArguments(callElement, psiFactory, expandedTypeArgumentsText)
            val newCallElement = ((usage.mainReference as KtSimpleNameReference).bindToFqName(
                expandedTypeFqName
            ) as KtExpression).getNonStrictParentOfType<KtCallElement>()
            return newCallElement?.getQualifiedExpressionForSelector() ?: newCallElement
        }
    }

    private fun specifyTypeArguments(
        callElement: KtElement,
        psiFactory: KtPsiFactory,
        expandedTypeArgumentsText: String?
    ) {
        if (callElement !is KtCallExpression) return
        val originalTypeArgumentList = callElement.typeArgumentList
        if (expandedTypeArgumentsText == null) {
            //the expanded type has no type arguments, e.g. when the alias declares an unused type parameter
            originalTypeArgumentList?.delete()
            return
        }
        val expandedTypeArgumentList = psiFactory.createTypeArguments(expandedTypeArgumentsText)
        if (originalTypeArgumentList != null) {
            shortenReferences(originalTypeArgumentList.replaced(expandedTypeArgumentList))
        } else {
            shortenReferences(callElement.addAfter(expandedTypeArgumentList, callElement.calleeExpression) as KtElement)
        }
    }

    private fun inlineIntoType(usage: KtUserType): KtElement? {
        analyze(usage) {
            val expandedType = usage.getParentOfType<KtTypeReference>(true)?.type?.fullyExpandedType ?: return null
            val expandedTypeText = expandedType.render(expandAliasRenderer(), position = Variance.INVARIANT)
            val isFunctionType = expandedType.isFunctionType || expandedType.isSuspendFunctionType
            val nullableType = usage.parent as? KtNullableType
            val typeReference = usage.parent as? KtTypeReference
            val isFunctionTypeReceiver = typeReference?.parent is KtFunctionTypeReceiver
            val replacementText = if (isFunctionType && isFunctionTypeReceiver) "($expandedTypeText)" else expandedTypeText
            val replacementTypeReference = KtPsiFactory(typeAlias.project).createType(replacementText)
            return when {
                nullableType != null -> nullableType.parent.replaced(replacementTypeReference)
                typeReference != null -> typeReference.replaced(replacementTypeReference)
                else -> usage.replaced(replacementTypeReference.typeElement!!)
            }.apply {
                shortenReferences(this)
            }
        }
    }

    private fun expandAliasRenderer(): KaTypeRenderer = KaTypeRendererForSource.WITH_QUALIFIED_NAMES.with {
        expandedTypeRenderingMode = KaExpandedTypeRenderingMode.RENDER_EXPANDED_TYPE
    }
}
