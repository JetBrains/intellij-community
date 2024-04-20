// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.changeSignature

import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.calls.KtCallableMemberCall
import org.jetbrains.kotlin.analysis.api.calls.KtImplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.calls.successfulCallOrNull
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtReceiverParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.types.KtFunctionalType
import org.jetbrains.kotlin.idea.base.psi.copied
import org.jetbrains.kotlin.idea.base.psi.isExpectDeclaration
import org.jetbrains.kotlin.idea.base.psi.setDefaultValue
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinModifiableParameterInfo
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinValVar
import org.jetbrains.kotlin.idea.refactoring.changeSignature.setValOrVar
import org.jetbrains.kotlin.idea.refactoring.changeSignature.toValVar
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.hasExpectModifier
import org.jetbrains.kotlin.psi.psiUtil.quoteIfNeeded
import org.jetbrains.kotlin.types.Variance

class KotlinParameterInfo(
    override val originalIndex: Int = -1,
    val originalType: KotlinTypeInfo,
    private var name: String,
    override var valOrVar: KotlinValVar,
    override var defaultValueForCall: KtExpression?,
    override var defaultValueAsDefaultParameter: Boolean,
    override var defaultValue: KtExpression?,
    val context: KtElement
) : KotlinModifiableParameterInfo {
    val oldName: String = name
    var currentType: KotlinTypeInfo = originalType

    override fun getName(): @NlsSafe String {
        return name
    }

    override fun setType(newType: String) {
        currentType = KotlinTypeInfo(newType, context)
    }

    override fun getOldIndex(): Int {
        return originalIndex
    }

    override val isNewParameter: Boolean
        get() = originalIndex == -1

    override fun getDefaultValue(): @NlsSafe String? {
        return null
    }

    override fun setName(name: @NlsSafe String) {
        this.name = name
    }

    override fun getTypeText(): @NlsSafe String {
        return currentType.text!!
    }

    override fun isUseAnySingleVariable(): Boolean {
        return false
    }

    override fun setUseAnySingleVariable(b: Boolean) {
        throw UnsupportedOperationException()
    }

    /**
     * Reference to parameter index
     *
     * 0, if refers to function's extension receiver in `this` expression
     * Int.MAX_VALUE, if refers to extension's/dispatch's receiver callable
     */
    val defaultValueParameterReferences: MutableMap<PsiReference, Int> = mutableMapOf<PsiReference, Int>();

    @OptIn(KtAllowAnalysisOnEdt::class)
    fun collectDefaultValueParameterReferences(callable: KtNamedDeclaration) {
        val expression = defaultValueForCall
        val file = expression?.containingFile as? KtFile ?: return
        if (!file.isPhysical && file.analysisContext == null) return
        allowAnalysisOnEdt {
            expression.accept(CollectParameterRefsVisitor(callable))
        }
    }


    fun getInheritedName(inheritor: KtCallableDeclaration?): String {
        val name = this.name.quoteIfNeeded()
        if (inheritor is KtFunctionLiteral && inheritor.valueParameters.size == 0 && oldIndex == 0) {
            //preserve default name
            return "it"
        }
        if (inheritor == null || oldIndex < 0 || oldIndex >= inheritor.valueParameters.size) return name

        val indexInVariableParameters = oldIndex - (if (inheritor.receiverTypeReference != null) 1 else 0)
        if (indexInVariableParameters < 0) return name
        val inheritedName = inheritor.valueParameters[indexInVariableParameters].name
        if (inheritedName == null || inheritedName == oldName) {
            return name
        }
        return inheritedName
    }

    @OptIn(KtAllowAnalysisOnEdt::class, KtAllowAnalysisFromWriteAction::class)
    fun requiresExplicitType(inheritedCallable: KtElement?): Boolean {
        if (inheritedCallable is KtFunctionLiteral) {
            allowAnalysisFromWriteAction {
                allowAnalysisOnEdt {
                    analyze(inheritedCallable) {
                        val expectedType = inheritedCallable.getExpectedType()
                        return expectedType == null || expectedType is KtFunctionalType
                    }
                }
            }
        }
        return true
    }

    private fun getOriginalParameter(inheritedCallable: KtDeclaration?): KtParameter? {
        val indexInVariableParameters = oldIndex - (if ((inheritedCallable as? KtCallableDeclaration)?.receiverTypeReference != null) 1 else 0)
        return (inheritedCallable as? KtCallableDeclaration)?.valueParameters?.getOrNull(indexInVariableParameters)
    }

    private fun buildNewParameter(inheritedCallable: KtDeclaration?, baseFunction: PsiElement, isInherited: Boolean): KtParameter {
        val psiFactory = KtPsiFactory(context.project)

        val buffer = StringBuilder()

        if (valOrVar != KotlinValVar.None && !(baseFunction is KtNamedDeclaration && baseFunction.isExpectDeclaration())) {
            buffer.append(valOrVar).append(' ')
        }

        buffer.append(getInheritedName((inheritedCallable as? KtCallableDeclaration)?.takeIf { isInherited }))

        if (requiresExplicitType(inheritedCallable)) {
            buffer.append(": ")
            buffer.append(
                try {
                    psiFactory.createType(typeText, inheritedCallable, baseFunction, Variance.IN_VARIANCE).getTypeText()
                } catch (_: Throwable) {
                    typeText
                }
            )
        }

        if (!isInherited) {
            defaultValue?.let { buffer.append(" = ").append(it.text) }
        }

        return psiFactory.createParameter(buffer.toString())
    }

    fun getDeclarationSignature(inheritedCallable: KtDeclaration?, baseFunction: PsiElement, isInherited: Boolean): KtParameter {
        val originalParameter = getOriginalParameter(inheritedCallable)
            ?: return buildNewParameter(inheritedCallable, baseFunction, isInherited)

        val psiFactory = KtPsiFactory(originalParameter.project)
        val newParameter = originalParameter.copied()

        if (valOrVar != newParameter.valOrVarKeyword.toValVar() && !(baseFunction is KtNamedDeclaration && baseFunction.isExpectDeclaration())) {
            newParameter.setValOrVar(valOrVar)
        }

        val newName = getInheritedName((inheritedCallable as? KtCallableDeclaration)?.takeIf { isInherited })
        if (newParameter.name != newName) {
            newParameter.setName(newName.quoteIfNeeded())
        }

        if ((newParameter.typeReference != null || requiresExplicitType(inheritedCallable)) &&
            (currentType.text != originalType.text || newParameter.typeReference == null) ) {
            newParameter.typeReference = psiFactory.createType(typeText, inheritedCallable, baseFunction, Variance.IN_VARIANCE)
        }

        if (!isInherited) {
            defaultValue?.let { newParameter.setDefaultValue(it) }
        }

        return newParameter
    }

    private inner class CollectParameterRefsVisitor(
        private val callableDeclaration: KtNamedDeclaration,
    ) : KtTreeVisitorVoid() {
        override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
            val ref = expression.mainReference
            val parameterIndex = targetToCollect(expression, ref) ?: return
            defaultValueParameterReferences[ref] = parameterIndex
        }

        private fun targetToCollect(expression: KtSimpleNameExpression, ref: KtReference): Int? {

            analyze(expression) {
                val target = ref.resolveToSymbol()
                val declarationSymbol = callableDeclaration.getSymbol() as? KtCallableSymbol ?: return null
                if (target is KtValueParameterSymbol) {
                    if (declarationSymbol is KtFunctionLikeSymbol && target.getContainingSymbol() == declarationSymbol) {
                        return declarationSymbol.valueParameters.indexOf(target) + (if ((callableDeclaration as? KtCallableDeclaration)?.receiverTypeReference != null) 1 else 0)
                    }

                    if (declarationSymbol.receiverParameter != null &&
                        (target.getContainingSymbol() as? KtConstructorSymbol)?.getContainingSymbol() == declarationSymbol.receiverParameter?.type?.expandedClassSymbol
                    ) {
                        return Int.MAX_VALUE
                    }
                    return null
                }

                if (target is KtPropertySymbol && declarationSymbol is KtConstructorSymbol) {
                    val parameterIndex = declarationSymbol.valueParameters.indexOfFirst { it.generatedPrimaryConstructorProperty == target }
                    if (parameterIndex >= 0) {
                        return parameterIndex
                    }
                }

                if (target is KtReceiverParameterSymbol && declarationSymbol.receiverParameter == target) {
                    //this which refers to function's receiver
                    return 0
                }

                val symbol = expression.resolveCall()?.successfulCallOrNull<KtCallableMemberCall<*, *>>()?.partiallyAppliedSymbol
                (symbol?.dispatchReceiver as? KtImplicitReceiverValue)?.symbol
                    ?.takeIf { it == declarationSymbol.receiverParameter || it == declarationSymbol.getContainingSymbol() }
                    ?.let { return Int.MAX_VALUE }
                (symbol?.extensionReceiver as? KtImplicitReceiverValue)?.symbol
                    ?.takeIf { it == declarationSymbol.receiverParameter || it == declarationSymbol.getContainingSymbol() }
                    ?.let { return Int.MAX_VALUE }

                if (expression.parent is KtThisExpression && declarationSymbol.receiverParameter == null) {
                    return Int.MAX_VALUE
                }
            }

            return null
        }
    }
}

fun defaultValOrVar(callableDescriptor: KtDeclaration): KotlinValVar {
    if (callableDescriptor is KtConstructor<*>) {
        val classOrObject = callableDescriptor.getContainingClassOrObject()
        if (classOrObject.isAnnotation() || classOrObject.isData() && callableDescriptor is KtPrimaryConstructor) {
            return KotlinValVar.Val
        }
    }

    if (callableDescriptor is KtClass) {
        if (callableDescriptor.isAnnotation() || callableDescriptor.isData()) {
            return KotlinValVar.Val
        }
    }

    return KotlinValVar.None
}
