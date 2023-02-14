// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.debugger.stepping.smartStepInto

import org.jetbrains.kotlin.coroutines.hasSuspendFunctionType
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.resolve.isInlineClass
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

    constructor(
        callerMethodDescriptor: CallableMemberDescriptor,
        parameterDescriptor: ValueParameterDescriptor,
        callerMethodOrdinal: Int,
        isNameMangledInBytecode: Boolean,
    ) : this(
        parameterName = parameterDescriptor.name.asString(),
        callerMethodOrdinal = callerMethodOrdinal,
        parameterIndex = countParameterIndex(callerMethodDescriptor, parameterDescriptor),
        isSuspend = parameterDescriptor.hasSuspendFunctionType,
        isSam = false,
        isNoinline = parameterDescriptor.isNoinline,
        isNameMangledInBytecode = isNameMangledInBytecode,
        methodName = OperatorNameConventions.INVOKE.asString(),
        callerMethodInfo = CallableMemberInfo(callerMethodDescriptor),
        isSamSuspendMethod = false
        )

    constructor(
        callerMethodDescriptor: CallableMemberDescriptor,
        parameterDescriptor: ValueParameterDescriptor,
        callerMethodOrdinal: Int,
        isNameMangledInBytecode: Boolean,
        methodName: String,
        isSamSuspendMethod: Boolean,
    ) : this(
        parameterName = parameterDescriptor.name.asString(),
        callerMethodOrdinal = callerMethodOrdinal,
        parameterIndex = countParameterIndex(callerMethodDescriptor, parameterDescriptor),
        isSuspend = parameterDescriptor.hasSuspendFunctionType,
        isSam = true,
        isNoinline = parameterDescriptor.isNoinline,
        isNameMangledInBytecode = isNameMangledInBytecode,
        methodName = methodName,
        callerMethodInfo = CallableMemberInfo(callerMethodDescriptor),
        isSamSuspendMethod = isSamSuspendMethod
    )

    fun getLabel() =
        "${callerMethodInfo.name}: $parameterName.$methodName()"
}

private fun countParameterIndex(callerMethodDescriptor: CallableMemberDescriptor, parameterDescriptor: ValueParameterDescriptor): Int {
    var resultIndex = parameterDescriptor.index
    if (callerMethodDescriptor.extensionReceiverParameter != null)
        resultIndex++
    if (callerMethodDescriptor.containingDeclaration.isInlineClass())
        resultIndex++

    return resultIndex
}
