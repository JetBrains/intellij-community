// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.base.util

import org.jetbrains.kotlin.name.FqName

object KotlinDebuggerConstants {
    const val KOTLIN_STRATA_NAME = "Kotlin"
    const val KOTLIN_DEBUG_STRATA_NAME = "KotlinDebug"

    const val THIS = "this"
    const val CAPTURED_PREFIX = "$"
    const val CAPTURED_THIS_FIELD = "this$0"
    const val LABELED_THIS_FIELD = THIS + "_"
    const val CAPTURED_LABELED_THIS_FIELD = CAPTURED_PREFIX + LABELED_THIS_FIELD
    const val THIS_IN_DEFAULT_IMPLS = "\$this"
    const val LABELED_THIS_PARAMETER = "$CAPTURED_PREFIX$THIS$"

    const val LOCAL_FUNCTION_VARIABLE_PREFIX = "\$fun$"
    const val CAPTURED_RECEIVER_FIELD = "receiver$0"
    const val RECEIVER_PARAMETER_NAME = "\$receiver"
    const val DESTRUCTURED_LAMBDA_ARGUMENT_VARIABLE_PREFIX = "\$dstr\$"

    const val INVOKE_SUSPEND_METHOD_NAME = "invokeSuspend"
    const val CONTINUATION_VARIABLE_NAME = "\$continuation"
    const val SUSPEND_FUNCTION_COMPLETION_PARAMETER_NAME = "\$completion"
    const val SUSPEND_IMPL_NAME_SUFFIX = "\$suspendImpl"

    const val INLINE_FUN_VAR_SUFFIX = "\$iv"
    const val INLINE_DECLARATION_SITE_THIS = "this_"
    const val INLINE_TRANSFORMATION_SUFFIX = "\$inlined"
    const val INLINE_SCOPE_NUMBER_SEPARATOR = '\\'

    val INLINE_ONLY_ANNOTATION_FQ_NAME = FqName("kotlin.internal.InlineOnly")
    val DEFAULT_CONSTRUCTOR_MARKER_FQ_NAME = FqName("kotlin.jvm.internal.DefaultConstructorMarker")
}
