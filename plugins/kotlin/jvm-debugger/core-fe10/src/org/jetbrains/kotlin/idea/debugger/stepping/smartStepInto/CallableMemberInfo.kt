// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.stepping.smartStepInto

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.symbols.KtConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolWithVisibility
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.idea.debugger.core.getByteCodeMethodName
import org.jetbrains.kotlin.idea.debugger.core.getContainingClassOrObjectSymbol
import org.jetbrains.kotlin.idea.debugger.core.isInlineClass

data class CallableMemberInfo(
    val isInvoke: Boolean,
    val isInlineClassMember: Boolean,
    val hasInlineClassInValueParameters: Boolean,
    val isInternalMethod: Boolean,
    val isExtension: Boolean,
    val isInline: Boolean,
    val name: String
) {
    val isNameMangledInBytecode = isInlineClassMember || hasInlineClassInValueParameters
}

context(KtAnalysisSession)
internal fun CallableMemberInfo(
    symbol: KtFunctionLikeSymbol,
    name: String = symbol.methodName()
) = CallableMemberInfo(
    isInvoke = symbol is KtFunctionSymbol && symbol.isBuiltinFunctionInvoke,
    isInlineClassMember = symbol.isInsideInlineClass(),
    hasInlineClassInValueParameters = symbol.containsInlineClassInValueArguments(),
    isInternalMethod = symbol is KtSymbolWithVisibility && symbol.visibility == Visibilities.Internal,
    isExtension = symbol.isExtension,
    isInline = symbol is KtFunctionSymbol && symbol.isInline,
    name = name,
)

context(KtAnalysisSession)
internal fun KtFunctionLikeSymbol.containsInlineClassInValueArguments(): Boolean =
    valueParameters.any { it.returnType.expandedClassSymbol?.isInlineClass() == true }

private fun KtFunctionLikeSymbol.methodName() = when (this) {
    is KtFunctionSymbol -> getByteCodeMethodName()
    is KtConstructorSymbol -> "<init>"
    else -> ""
}

context(KtAnalysisSession)
internal fun KtFunctionLikeSymbol.isInsideInlineClass(): Boolean = getContainingClassOrObjectSymbol()?.isInlineClass() == true
