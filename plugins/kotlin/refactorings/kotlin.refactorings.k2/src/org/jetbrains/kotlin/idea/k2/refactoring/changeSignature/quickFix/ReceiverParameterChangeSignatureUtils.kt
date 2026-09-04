// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiRecursiveVisitor
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.KaSimpleCall
import org.jetbrains.kotlin.analysis.api.resolution.KaSimpleOrMultiCall
import org.jetbrains.kotlin.analysis.api.resolution.resolveSuccessfulCall
import org.jetbrains.kotlin.analysis.api.resolution.resolveSuccessfulSymbol
import org.jetbrains.kotlin.analysis.api.resolution.resolveSuccessfulSymbols
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaReceiverParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaTypeParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.containingDeclaration
import org.jetbrains.kotlin.analysis.api.symbols.receiverType
import org.jetbrains.kotlin.analysis.api.symbols.symbol
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeParameterType
import org.jetbrains.kotlin.analysis.api.types.buildSubstitutor
import org.jetbrains.kotlin.analysis.api.types.defaultType
import org.jetbrains.kotlin.analysis.api.types.isSubtypeOf
import org.jetbrains.kotlin.analysis.api.types.semanticallyEquals
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.idea.base.psi.EditCommaSeparatedListHelper
import org.jetbrains.kotlin.idea.base.psi.setCallableReceiverTypeReference
import org.jetbrains.kotlin.idea.codeinsight.utils.resolveExpression
import org.jetbrains.kotlin.idea.codeinsight.utils.typeIfSafeToResolve
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinChangeInfo
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinChangeSignatureProcessor
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinMethodDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.getThisReceiverOwner
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassLiteralExpression
import org.jetbrains.kotlin.psi.KtDestructuringDeclarationEntry
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtForExpression
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.KtTypeConstraint
import org.jetbrains.kotlin.psi.KtTypeConstraintList
import org.jetbrains.kotlin.psi.KtTypeParameter
import org.jetbrains.kotlin.psi.KtTypeParameterList
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.forEachDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType
import org.jetbrains.kotlin.psi.psiUtil.getParentOfTypesAndPredicate
import org.jetbrains.kotlin.psi.psiUtil.getPrevSiblingIgnoringWhitespaceAndComments
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.resolution.KtResolvable
import org.jetbrains.kotlin.resolution.KtResolvableCall

object ReceiverParameterChangeSignatureUtils {
    context(_: KaSession)
    fun collectUsedTypeParameters(typeReference: KtTypeReference): Set<KaTypeParameterSymbol> =
        sequenceOf(typeReference)
            .plus(typeReference.collectDescendantsOfType<KtTypeReference>())
            .mapNotNullTo(mutableSetOf()) { (it.typeIfSafeToResolve as? KaTypeParameterType)?.symbol }

    fun removeReceiverParameter(project: Project, callableDeclaration: KtCallableDeclaration, textForReceiver: String? = null) {
        val typeReference = callableDeclaration.receiverTypeReference ?: return
        val typeParameters = typeParameters(typeReference)

        if (textForReceiver != null) {
            runWriteAction {
                callableDeclaration.forEachDescendantOfType<KtThisExpression> {
                    if (it.text == textForReceiver) it.labelQualifier?.delete()
                }
                callableDeclaration.setCallableReceiverTypeReference(null)
            }
        } else {
            val methodDescriptor = KotlinMethodDescriptor(callableDeclaration)
            val changeInfo = KotlinChangeInfo(methodDescriptor)
            changeInfo.removeParameter(0)
            KotlinChangeSignatureProcessor(project, changeInfo).run()
        }

        removeUnusedTypeParameters(typeParameters)
    }

    context(_: KaSession)
    fun isReceiverUsedInside(
        callableDeclaration: KtCallableDeclaration,
        usedReifiedTypeParametersInReceiver: Set<KaTypeParameterSymbol>,
    ): Boolean {
        val callableSymbol: KaDeclarationSymbol = callableDeclaration.symbol
        var used = false
        callableDeclaration.acceptChildren(object : KtVisitorVoid(), PsiRecursiveVisitor {
            override fun visitKtElement(element: KtElement) {
                if (used) return
                element.acceptChildren(this)

                if (isUsageOfSymbol(callableSymbol, element) || isUsageOfReifiedType(usedReifiedTypeParametersInReceiver, element)) {
                    used = true
                }
            }
        })
        return used
    }

    private fun typeParameters(typeReference: KtTypeReference): List<KtTypeParameter> {
        val parameterParent = typeReference.getParentOfTypesAndPredicate(
            true,
            KtNamedFunction::class.java, KtProperty::class.java, KtClass::class.java,
        ) { true }
        return typeReference.typeElement
            ?.collectDescendantsOfType<KtNameReferenceExpression>()
            ?.mapNotNull {
                val typeParameter = it.reference?.resolve() as? KtTypeParameter ?: return@mapNotNull null
                val parent = typeParameter.getParentOfTypesAndPredicate(
                    true,
                    KtNamedFunction::class.java, KtProperty::class.java, KtClass::class.java,
                ) { true }
                if (parent == parameterParent) typeParameter else null
            } ?: emptyList()
    }

    private fun removeUnusedTypeParameters(typeParameters: List<KtTypeParameter>) {
        val unusedTypeParams = typeParameters.filter { typeParameter ->
            !ReferencesSearch.search(typeParameter).anyMatch { (it as? KtSimpleNameReference)?.expression?.parent !is KtTypeConstraint }
        }
        if (unusedTypeParams.isEmpty()) return
        runWriteAction {
            unusedTypeParams.forEach { typeParameter ->
                val typeParameterList = typeParameter.parent as? KtTypeParameterList ?: return@forEach
                val typeConstraintList = typeParameterList.parent.getChildOfType<KtTypeConstraintList>()
                if (typeConstraintList != null) {
                    val typeConstraint = typeConstraintList.constraints.find { it.subjectTypeParameterName?.text == typeParameter.text }
                    if (typeConstraint != null) EditCommaSeparatedListHelper.removeItem(typeConstraint)
                    if (typeConstraintList.constraints.isEmpty()) {
                        val prev = typeConstraintList.getPrevSiblingIgnoringWhitespaceAndComments()
                        if (prev?.node?.elementType == KtTokens.WHERE_KEYWORD) prev.delete()
                    }
                }
                if (typeParameterList.parameters.size == 1)
                    typeParameterList.delete()
                else
                    EditCommaSeparatedListHelper.removeItem(typeParameter)
            }
        }
    }

    context(_: KaSession)
    private fun KaSimpleCall<*, *>.hasContextReceiverOfType(type: KaType): Boolean {
        val substitutor = buildSubstitutor {
            substitutions(typeArgumentsMapping)
        }
        return symbol.contextReceivers.any { type.isSubtypeOf(substitutor.substitute(it.type)) }
    }

    context(_: KaSession)
    private fun isUsageOfReifiedType(reifiedTypes: Set<KaTypeParameterSymbol>, element: KtElement): Boolean {
        val parentFunction = element.parentOfType<KtFunction>() ?: return false
        if (element !is KtExpression) return false
        if (element.parents.none { it == parentFunction.bodyBlockExpression || it == parentFunction.bodyExpression }) return false
        return reifiedTypes.contains(element.resolveExpression())
    }

    context(_: KaSession)
    private fun isUsageOfSymbol(symbol: KaDeclarationSymbol, element: KtElement): Boolean {
        if (element !is KtExpression) return false

        val receiverType = (symbol as? KaCallableSymbol)?.receiverType
        fun isUsageOfSymbolInResolvedCall(resolvedCall: KaSimpleOrMultiCall): Boolean = when (resolvedCall) {
            is KaSimpleCall<*, *> -> {
                resolvedCall.dispatchReceiver?.getThisReceiverOwner() == symbol ||
                        resolvedCall.extensionReceiver?.getThisReceiverOwner() == symbol ||
                        resolvedCall.contextArguments.any { it.getThisReceiverOwner() == symbol } ||
                        (receiverType != null && resolvedCall.hasContextReceiverOfType(receiverType))
            }

            else -> false
        }

        when (element) {
            is KtClassLiteralExpression -> {
                val typeParameterType =
                    ((element.receiverExpression as? KtResolvable)?.resolveSuccessfulSymbol() as? KaTypeParameterSymbol)?.defaultType
                if (typeParameterType != null && receiverType?.semanticallyEquals(typeParameterType) == true) {
                    return true
                }
            }
        }

        fun processOperators(e: KtResolvableCall): Boolean {
            return e.resolveSuccessfulSymbols().filterIsInstance<KaFunctionSymbol>()
                .any { receiverType?.symbol == it.containingDeclaration }
        }

        return when (element) {
            is KtThisExpression -> {
                val referencedSymbol = element.resolveSuccessfulSymbol()
                referencedSymbol is KaReceiverParameterSymbol && referencedSymbol.owningCallableSymbol == symbol
            }

            is KtDestructuringDeclarationEntry -> processOperators(element)

            is KtProperty -> {
                val propertyDelegate = element.delegate
                propertyDelegate != null && processOperators(propertyDelegate)
            }

            is KtForExpression -> processOperators(element)

            is KtResolvableCall -> {
                val resolvedCall = element.resolveSuccessfulCall() ?: return false
                isUsageOfSymbolInResolvedCall(resolvedCall)
            }

            else -> false
        }
    }
}
