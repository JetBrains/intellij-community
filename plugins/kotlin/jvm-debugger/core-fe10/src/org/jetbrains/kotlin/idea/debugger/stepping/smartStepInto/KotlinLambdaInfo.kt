// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.debugger.stepping.smartStepInto

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtValueParameterSymbol
import org.jetbrains.kotlin.util.OperatorNameConventions

data class KotlinLambdaInfo(
    val parameterName: String,
    val callerMethodOrdinal: Int,
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

context(KtAnalysisSession)
internal fun KotlinLambdaInfo(
    methodSymbol: KtFunctionLikeSymbol,
    argumentSymbol: KtValueParameterSymbol,
    callerMethodOrdinal: Int,
    isNameMangledInBytecode: Boolean,
    methodName: String = OperatorNameConventions.INVOKE.asString(),
    isSam: Boolean = false,
    isSamSuspendMethod: Boolean = false,
) = KotlinLambdaInfo(
    parameterName = argumentSymbol.name.asString(),
    callerMethodOrdinal = callerMethodOrdinal,
    parameterIndex = countParameterIndex(methodSymbol, argumentSymbol),
    isSuspend = argumentSymbol.returnType.isSuspendFunctionType,
    isSam = isSam,
    isNoinline = argumentSymbol.isNoinline,
    isNameMangledInBytecode = isNameMangledInBytecode,
    methodName = methodName,
    callerMethodInfo = CallableMemberInfo(methodSymbol),
    isSamSuspendMethod = isSamSuspendMethod,
)

context(KtAnalysisSession)
private fun countParameterIndex(methodSymbol: KtFunctionLikeSymbol, argumentSymbol: KtValueParameterSymbol): Int {
    var resultIndex = methodSymbol.valueParameters.indexOf(argumentSymbol)

    if (methodSymbol.isExtension)
        resultIndex++
    if (methodSymbol.isInsideInlineClass())
        resultIndex++

    return resultIndex
}
