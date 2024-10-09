// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.stepping.smartStepInto

import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolVisibility
import org.jetbrains.kotlin.analysis.api.symbols.isLocal
import org.jetbrains.kotlin.idea.debugger.base.util.KotlinDebuggerConstants
import org.jetbrains.kotlin.idea.debugger.core.getByteCodeMethodName
import org.jetbrains.kotlin.idea.debugger.core.getContainingClassOrObjectSymbol
import org.jetbrains.kotlin.idea.debugger.core.isInlineClass

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
) {
    val isNameMangledInBytecode = isInlineClassMember || hasInlineClassInParameters
}

context(KaSession)
internal fun CallableMemberInfo(
    symbol: KaFunctionSymbol,
    ordinal: Int = 0,
    name: String = symbol.methodName()
): CallableMemberInfo {
    val isInvoke = symbol is KaNamedFunctionSymbol && symbol.isBuiltinFunctionInvoke
    val isSuspend = symbol.isSuspend()
    val effectiveName = if (isInvoke && isSuspend) KotlinDebuggerConstants.INVOKE_SUSPEND_METHOD_NAME else name
    return CallableMemberInfo(
        isInvoke = isInvoke,
        isSuspend = isSuspend,
        isInlineClassMember = symbol.isInsideInlineClass(),
        hasInlineClassInParameters = symbol.containsInlineClassInParameters(),
        isInternalMethod = symbol.visibility == KaSymbolVisibility.INTERNAL,
        isExtension = symbol.isExtension,
        isInline = symbol is KaNamedFunctionSymbol && symbol.isInline,
        name = effectiveName,
        isLocal = symbol.isLocal,
        ordinal = ordinal,
    )
}

internal fun KaFunctionSymbol.isSuspend(): Boolean = this is KaNamedFunctionSymbol && this.isSuspend

context(KaSession)
@OptIn(KaExperimentalApi::class)
internal fun KaFunctionSymbol.containsInlineClassInParameters(): Boolean =
    valueParameters.any { it.returnType.expandedSymbol?.isInlineClass() == true }
            || receiverParameter?.returnType?.expandedSymbol?.isInlineClass() == true
            || contextReceivers.any { it.type.expandedSymbol?.isInlineClass() == true }

context(KaSession)
private fun KaFunctionSymbol.methodName() = when (this) {
    is KaNamedFunctionSymbol -> getByteCodeMethodName()
    is KaConstructorSymbol -> "<init>"
    else -> ""
}

context(KaSession)
internal fun KaFunctionSymbol.isInsideInlineClass(): Boolean = getContainingClassOrObjectSymbol()?.isInlineClass() == true

