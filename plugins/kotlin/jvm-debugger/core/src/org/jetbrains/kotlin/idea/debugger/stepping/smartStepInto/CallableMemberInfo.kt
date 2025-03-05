// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.stepping.smartStepInto

import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolVisibility
import org.jetbrains.kotlin.analysis.api.symbols.isLocal
import org.jetbrains.kotlin.idea.debugger.base.util.KotlinDebuggerConstants
import org.jetbrains.kotlin.idea.debugger.core.getContainingClassOrObjectSymbol
import org.jetbrains.kotlin.idea.debugger.core.isInlineClass
import org.jetbrains.kotlin.idea.debugger.core.methodName

data class CallableMemberInfo(
    val isInvoke: Boolean,
    val isSuspend: Boolean,
    val isInlineClassMember: Boolean,
    val hasInlineClassInParameters: Boolean,
    val isInternalMethod: Boolean,
    val isExtension: Boolean,
    val isInline: Boolean,
    val name: String,
    val isLocal: Boolean,
    var ordinal: Int,
    val isEqualsNullCall: Boolean,
) {
    val isNameMangledInBytecode = isInlineClassMember || hasInlineClassInParameters
}

internal fun KaSession.CallableMemberInfo(
    symbol: KaFunctionSymbol,
    ordinal: Int = 0,
    isEqualsNullCall: Boolean = false,
): CallableMemberInfo {
    val name = methodName(symbol) ?: ""
    val isInvoke = symbol.isInvoke()
    val isSuspend = symbol.isSuspend()
    val effectiveName = if (isInvoke && isSuspend) KotlinDebuggerConstants.INVOKE_SUSPEND_METHOD_NAME else name
    return CallableMemberInfo(
        isInvoke = isInvoke,
        isSuspend = isSuspend,
        isInlineClassMember = isInsideInlineClass(symbol),
        hasInlineClassInParameters = containsInlineClassInParameters(symbol),
        isInternalMethod = symbol.visibility == KaSymbolVisibility.INTERNAL,
        isExtension = symbol.isExtension,
        isInline = symbol is KaNamedFunctionSymbol && symbol.isInline,
        name = effectiveName,
        isLocal = symbol.isLocal,
        ordinal = ordinal,
        isEqualsNullCall = isEqualsNullCall,
    )
}

internal fun KaFunctionSymbol.isSuspend(): Boolean = this is KaNamedFunctionSymbol && this.isSuspend
internal fun KaFunctionSymbol.isInvoke(): Boolean = this is KaNamedFunctionSymbol && this.isBuiltinFunctionInvoke

@OptIn(KaExperimentalApi::class)
internal fun KaSession.containsInlineClassInParameters(symbol: KaFunctionSymbol): Boolean =
    symbol.valueParameters.any { isInlineClass(it.returnType.expandedSymbol) }
            || isInlineClass(symbol.receiverParameter?.returnType?.expandedSymbol)
            || symbol.contextReceivers.any { isInlineClass(it.type.expandedSymbol) }

internal fun KaSession.isInsideInlineClass(symbol: KaFunctionSymbol): Boolean =
    isInlineClass(getContainingClassOrObjectSymbol(symbol))

