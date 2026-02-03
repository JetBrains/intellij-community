// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.base.util

import org.jetbrains.kotlin.name.FqName

object KotlinDebuggerConstants {
    const val DESTRUCTURED_LAMBDA_ARGUMENT_VARIABLE_PREFIX = "\$dstr\$"

    const val INVOKE_SUSPEND_METHOD_NAME = "invokeSuspend"
    const val SUSPEND_IMPL_NAME_SUFFIX = "\$suspendImpl"

    const val INLINE_FUN_VAR_SUFFIX = "\$iv"
    const val INLINE_TRANSFORMATION_SUFFIX = "\$inlined"
    const val INLINE_SCOPE_NUMBER_SEPARATOR = '\\'

    val INLINE_ONLY_ANNOTATION_FQ_NAME = FqName("kotlin.internal.InlineOnly")
    val DEFAULT_CONSTRUCTOR_MARKER_FQ_NAME = FqName("kotlin.jvm.internal.DefaultConstructorMarker")
}
