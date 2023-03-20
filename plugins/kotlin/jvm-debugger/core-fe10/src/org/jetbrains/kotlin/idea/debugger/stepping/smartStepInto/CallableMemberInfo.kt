// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.stepping.smartStepInto

import org.jetbrains.kotlin.builtins.functions.FunctionInvokeDescriptor
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.resolve.descriptorUtil.isExtension
import org.jetbrains.kotlin.resolve.inline.InlineUtil
import org.jetbrains.kotlin.resolve.isInlineClass
import org.jetbrains.kotlin.resolve.isInlineClassType

data class CallableMemberInfo(
    val isInvoke: Boolean,
    val isInlineClassMember: Boolean,
    val hasInlineClassInValueParameters: Boolean,
    val isExtension: Boolean,
    val isInline: Boolean,
    val name: String
) {
    val isNameMangledInBytecode = isInlineClassMember || hasInlineClassInValueParameters

    constructor(descriptor: CallableMemberDescriptor) :
            this(
                descriptor is FunctionInvokeDescriptor,
                descriptor.containingDeclaration.isInlineClass(),
                descriptor.containsInlineClassInValueArguments(),
                descriptor.isExtension,
                InlineUtil.isInline(descriptor),
                descriptor.getMethodName()
            )
}

internal fun CallableMemberDescriptor.containsInlineClassInValueArguments(): Boolean =
    valueParameters.any { it.type.isInlineClassType() }
