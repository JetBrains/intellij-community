// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.debugger.stepping.smartStepInto

import org.jetbrains.kotlin.coroutines.isSuspendLambda
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.resolve.inline.InlineUtil
import org.jetbrains.kotlin.util.OperatorNameConventions

data class KotlinLambdaInfo(
    val parameterName: String,
    val callerMethodOrdinal: Int,
    val callerMethodName: String,
    val parameterIndex: Int,
    val isSuspend: Boolean,
    val isCallerMethodInline: Boolean,
    val isSam: Boolean,
    val isNoinline: Boolean,
    val methodName: String
) {
    val isInline = isCallerMethodInline && !isNoinline && !isSam

    constructor(
        callerMethodDescriptor: CallableMemberDescriptor,
        parameterDescriptor: ValueParameterDescriptor,
        callerMethodOrdinal: Int,
        isSam: Boolean = false,
        methodName: String = OperatorNameConventions.INVOKE.asString()
    ) : this(
            parameterDescriptor.name.asString(),
            callerMethodOrdinal,
            callerMethodDescriptor.getMethodName(),
            countParameterIndex(callerMethodDescriptor, parameterDescriptor),
            parameterDescriptor.isSuspendLambda,
            InlineUtil.isInline(callerMethodDescriptor),
            isSam,
            parameterDescriptor.isNoinline,
            methodName
        )

    fun getLabel() =
        "$callerMethodName: $parameterName.$methodName()"
}

private fun countParameterIndex(callerMethodDescriptor: CallableMemberDescriptor, parameterDescriptor: ValueParameterDescriptor) =
    if (callerMethodDescriptor.extensionReceiverParameter == null)
        parameterDescriptor.index
    else
        parameterDescriptor.index + 1
