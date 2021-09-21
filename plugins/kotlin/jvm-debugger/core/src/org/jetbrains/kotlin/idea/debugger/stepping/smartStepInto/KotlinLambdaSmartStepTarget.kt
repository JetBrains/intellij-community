// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.stepping.smartStepInto

import com.intellij.util.Range
import org.jetbrains.kotlin.builtins.isSuspendFunctionType
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.resolve.inline.InlineUtil
import org.jetbrains.kotlin.util.OperatorNameConventions
import javax.swing.Icon

class KotlinLambdaSmartStepTarget(
    resultingDescriptor: CallableDescriptor,
    parameter: ValueParameterDescriptor,
    highlightElement: KtFunction,
    lines: Range<Int>,
    val isInline: Boolean = InlineUtil.isInline(resultingDescriptor),
    val isSuspend: Boolean = parameter.type.isSuspendFunctionType,
    val methodName: String = OperatorNameConventions.INVOKE.asString()
) : KotlinSmartStepTarget(
        calcLabel(resultingDescriptor, parameter, methodName),
        highlightElement,
        true,
        lines
) {
    override fun createMethodFilter() =
        KotlinLambdaMethodFilter(this)

    override fun getIcon(): Icon = KotlinIcons.LAMBDA

    fun getLambda() = highlightElement as KtFunction
}

private fun calcLabel(descriptor: DeclarationDescriptor, parameter: ValueParameterDescriptor, functionName: String) =
    "${descriptor.name.asString()}: ${parameter.name.asString()}.$functionName()"
