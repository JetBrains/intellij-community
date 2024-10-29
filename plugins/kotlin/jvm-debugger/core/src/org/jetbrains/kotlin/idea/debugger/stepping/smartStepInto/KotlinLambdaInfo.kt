// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.debugger.stepping.smartStepInto

import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.util.OperatorNameConventions

data class KotlinLambdaInfo(
    val parameterName: String,
    val parameterIndex: Int,
    val isSuspend: Boolean,
    val isSam: Boolean,
    val isNoinline: Boolean,
    val isNameMangledInBytecode: Boolean,
    val methodName: String,
    val callerMethodInfo: CallableMemberInfo,
    val isSamSuspendMethod: Boolean
) {
    val isInline = callerMethodInfo.isInline && !isNoinline && !isSam

    fun getLabel() =
        "${callerMethodInfo.name}: $parameterName.$methodName()"
}

context(KaSession)
internal fun KotlinLambdaInfo(
    methodSymbol: KaFunctionSymbol,
    argumentSymbol: KaValueParameterSymbol,
    callerMethodOrdinal: Int,
    isNameMangledInBytecode: Boolean,
    methodName: String = OperatorNameConventions.INVOKE.asString(),
    isSam: Boolean = false,
    isSamSuspendMethod: Boolean = false,
) = KotlinLambdaInfo(
    parameterName = argumentSymbol.name.asString(),
    parameterIndex = countParameterIndex(methodSymbol, argumentSymbol),
    isSuspend = argumentSymbol.returnType.isSuspendFunctionType,
    isSam = isSam,
    isNoinline = argumentSymbol.isNoinline,
    isNameMangledInBytecode = isNameMangledInBytecode,
    methodName = methodName,
    callerMethodInfo = CallableMemberInfo(methodSymbol, callerMethodOrdinal),
    isSamSuspendMethod = isSamSuspendMethod,
)

context(KaSession)
@OptIn(KaExperimentalApi::class)
private fun countParameterIndex(methodSymbol: KaFunctionSymbol, argumentSymbol: KaValueParameterSymbol): Int {
    var resultIndex = methodSymbol.valueParameters.indexOf(argumentSymbol)

    if (methodSymbol.isExtension)
        resultIndex++
    if (methodSymbol.isInsideInlineClass())
        resultIndex++
    resultIndex += methodSymbol.contextReceivers.size

    return resultIndex
}
