// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.stepping.smartStepInto

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.symbols.KaConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolWithVisibility
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.idea.debugger.core.getByteCodeMethodName
import org.jetbrains.kotlin.idea.debugger.core.getContainingClassOrObjectSymbol
import org.jetbrains.kotlin.idea.debugger.core.isInlineClass

data class CallableMemberInfo(
    val isInvoke: Boolean,
    val isSuspend: Boolean,
    val isInlineClassMember: Boolean,
    val hasInlineClassInValueParameters: Boolean,
    val isInternalMethod: Boolean,
    val isExtension: Boolean,
    val isInline: Boolean,
    val name: String,
    var ordinal: Int,
) {
    val isNameMangledInBytecode = isInlineClassMember || hasInlineClassInValueParameters
}

context(KtAnalysisSession)
internal fun CallableMemberInfo(
    symbol: KaFunctionLikeSymbol,
    ordinal: Int = 0,
    name: String = symbol.methodName()
): CallableMemberInfo {
    val isInvoke = symbol is KaFunctionSymbol && symbol.isBuiltinFunctionInvoke
    val isSuspend = symbol is KaFunctionSymbol && symbol.isSuspend
    val effectiveName = if (isInvoke && isSuspend) "invokeSuspend" else name
    return CallableMemberInfo(
        isInvoke = isInvoke,
        isSuspend = isSuspend,
        isInlineClassMember = symbol.isInsideInlineClass(),
        hasInlineClassInValueParameters = symbol.containsInlineClassInValueArguments(),
        isInternalMethod = symbol is KtSymbolWithVisibility && symbol.visibility == Visibilities.Internal,
        isExtension = symbol.isExtension,
        isInline = symbol is KaFunctionSymbol && symbol.isInline,
        name = effectiveName,
        ordinal = ordinal,
    )
}

context(KtAnalysisSession)
internal fun KaFunctionLikeSymbol.containsInlineClassInValueArguments(): Boolean =
    valueParameters.any { it.returnType.expandedClassSymbol?.isInlineClass() == true }

private fun KaFunctionLikeSymbol.methodName() = when (this) {
    is KaFunctionSymbol -> getByteCodeMethodName()
    is KaConstructorSymbol -> "<init>"
    else -> ""
}

context(KtAnalysisSession)
internal fun KaFunctionLikeSymbol.isInsideInlineClass(): Boolean = getContainingClassOrObjectSymbol()?.isInlineClass() == true
