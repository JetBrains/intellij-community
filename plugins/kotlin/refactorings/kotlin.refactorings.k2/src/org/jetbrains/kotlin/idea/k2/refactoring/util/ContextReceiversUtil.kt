// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.util

import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.KaImplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.KaReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.KaVariableAccessCall
import org.jetbrains.kotlin.analysis.api.resolution.singleCallOrNull
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.idea.base.analysis.api.utils.unwrapSmartCasts
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinParameterInfo
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtSimpleNameExpression

@OptIn(KaExperimentalApi::class)
internal fun KaSession.createReplacementForContextArgument(receiverValue: KaReceiverValue): String? {
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

internal fun KaSession.createContextArgumentReplacementMapForVariableAccess(
    callElement: KtSimpleNameExpression
): Map<Int, SmartPsiElementPointer<KtExpression>>? =
    createContextArgumentReplacementMap<KaVariableAccessCall>(callElement)

internal fun KaSession.createContextArgumentReplacementMapForFunctionCall(
    callElement: KtCallElement
): Map<Int, SmartPsiElementPointer<KtExpression>>? =
    createContextArgumentReplacementMap<KaFunctionCall<*>>(callElement)

@OptIn(KaExperimentalApi::class)
private inline fun <reified T : KaCallableMemberCall<*, *>> KaSession.createContextArgumentReplacementMap(
    callElement: KtElement
): Map<Int, SmartPsiElementPointer<KtExpression>>? {
    val callInfo = callElement.resolveToCall()
    val kaCall = callInfo?.singleCallOrNull<T>()
        ?: return null
    val psiFactory = KtPsiFactory.contextual(callElement)
    val map = mutableMapOf<Int, SmartPsiElementPointer<KtExpression>>()
    kaCall.partiallyAppliedSymbol.contextArguments.forEachIndexed { idx, receiverValue ->
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
