// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.util

import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.KaImplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.KaReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.KaSimpleCall
import org.jetbrains.kotlin.analysis.api.resolution.KaVariableAccessCall
import org.jetbrains.kotlin.analysis.api.resolution.resolveSuccessfulCall
import org.jetbrains.kotlin.analysis.api.resolution.simple
import org.jetbrains.kotlin.analysis.api.resolution.single
import org.jetbrains.kotlin.analysis.api.resolution.tryResolveCall
import org.jetbrains.kotlin.analysis.api.symbols.KaAnonymousObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaContextParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaReceiverParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.containingSymbol
import org.jetbrains.kotlin.analysis.api.symbols.name
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.idea.base.analysis.api.utils.allowAnalysisFromWriteActionInEdt
import org.jetbrains.kotlin.idea.base.analysis.api.utils.unwrapSmartCasts
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinChangeInfoBase
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinParameterInfo
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassLikeDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.resolution.KtResolvableCall

context(session: KaSession)
internal fun createReplacementForContextArgument(receiverValue: KaReceiverValue): String? {
    return when (val symbol = (receiverValue.unwrapSmartCasts() as? KaImplicitReceiverValue)?.symbol) {
        is KaReceiverParameterSymbol -> symbol.containingSymbol?.name?.asString()?.let { "this@$it" } ?: "this"

        is KaContextParameterSymbol -> {
            val name = symbol.name
            if (!name.isSpecial) {
                name.asString()
            } else {
                val returnTypeSymbol = symbol.returnType.symbol
                val superOfAnonymous = (returnTypeSymbol as? KaAnonymousObjectSymbol)?.superTypes?.firstOrNull()?.symbol
                val className =
                    ((superOfAnonymous ?: returnTypeSymbol) as? KaNamedClassSymbol)?.name
                        ?.takeUnless { it.isSpecial }?.asString()
                if (className != null) "contextOf<$className>()" else "contextOf()"
            }
        }

        else -> {
            null
        }
    }
}

context(session: KaSession)
internal fun createContextArgumentReplacementMapForVariableAccess(
    callElement: KtSimpleNameExpression
): Map<Int, SmartPsiElementPointer<KtExpression>>? =
    createContextArgumentReplacementMap<KaVariableAccessCall>(callElement)

context(session: KaSession)
internal fun createContextArgumentReplacementMapForFunctionCall(
    callElement: KtCallElement
): Map<Int, SmartPsiElementPointer<KtExpression>>? =
    createContextArgumentReplacementMap<KaFunctionCall<*>>(callElement)

@OptIn(KaExperimentalApi::class)
context(session: KaSession)
private inline fun <reified T : KaSimpleCall<*, *>> createContextArgumentReplacementMap(
    callElement: KtElement
): Map<Int, SmartPsiElementPointer<KtExpression>>? {
    val singleCall = (callElement as? KtResolvableCall)?.tryResolveCall()?.single?.simple as? T ?: return null
    val psiFactory = KtPsiFactory.contextual(callElement)
    val map = mutableMapOf<Int, SmartPsiElementPointer<KtExpression>>()
    singleCall.contextArguments.forEachIndexed { idx, receiverValue ->
        val replacement = createReplacementForContextArgument(receiverValue) ?: return@forEachIndexed
        map[idx] = psiFactory.createExpression(replacement).createSmartPointer()
    }
    return map
}

internal fun createReplacementReceiverArgumentExpression(
    psiFactory: KtPsiFactory,
    newReceiverInfo: KotlinParameterInfo,
    argumentMapping: Map<Int, SmartPsiElementPointer<KtExpression>>,
    contextParameters: Map<Int, SmartPsiElementPointer<KtExpression>>?,
): KtExpression {
    val receiverArgument = when {
        !newReceiverInfo.wasContextParameter -> argumentMapping[newReceiverInfo.oldIndex]?.element
        else -> contextParameters?.get(newReceiverInfo.oldIndex)?.element
    }
    val defaultValueForCall = newReceiverInfo.defaultValueForCall
    return receiverArgument?.let { psiFactory.createExpression(it.text) }
        ?: defaultValueForCall
        ?: psiFactory.createExpression("contextOf<${newReceiverInfo.currentType.text}>()").takeIf {
            newReceiverInfo.wasContextParameter && newReceiverInfo.currentType.text != null
        }
        ?: psiFactory.createExpression("_")
}

internal fun collectContextParameterValues(
    changeInfo: KotlinChangeInfoBase,
    contextArgumentPointer: (KotlinParameterInfo) -> SmartPsiElementPointer<KtExpression>?,
): List<String> =
    changeInfo.newParameters.filter { it.isContextParameter && !it.wasContextParameter }.mapNotNull { parameter ->
        val pointer = contextArgumentPointer(parameter) ?: return@mapNotNull parameter.defaultValueForCall?.text
        val expression = pointer.element ?: return@mapNotNull null
        when {
            expression is KtThisExpression -> null
            (expression.mainReference?.resolve() as? KtParameter)?.isContextParameter == true -> null
            allowAnalysisFromWriteActionInEdt(expression) { isProvidedByEnclosingContext(expression) } -> null
            else -> expression.text
        }
    }

@OptIn(KaExperimentalApi::class)
context(session: KaSession)
internal fun isProvidedByEnclosingContext(expression: KtExpression): Boolean {
    val contextNames = setOf("with", "context")
    val contextFQNames = contextNames.map { FqName("kotlin.$it") }

    val containingLambda = PsiTreeUtil.getParentOfType(expression, KtLambdaArgument::class.java, true, KtClassLikeDeclaration::class.java) ?: return false
    val referencedDeclaration = expression.mainReference?.resolve() ?: return false
    return generateSequence(containingLambda) {
        PsiTreeUtil.getParentOfType(it, KtLambdaArgument::class.java, true, KtClassLikeDeclaration::class.java)
    }.any { lambdaArgument ->
        val call = lambdaArgument.parent as? KtCallExpression ?: return@any false
        val text = call.calleeExpression?.text ?: return@any false
        if (text !in contextNames) return@any false
        val callableId = call.resolveSuccessfulCall()?.signature?.callableId?.asSingleFqName()
        if (callableId !in contextFQNames) return@any false
        call.valueArguments.any {
            it.getArgumentExpression()?.mainReference?.resolve() == referencedDeclaration
        }
    }
}