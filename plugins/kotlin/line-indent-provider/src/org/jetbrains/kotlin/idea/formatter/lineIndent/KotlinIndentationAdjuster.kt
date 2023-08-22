// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.formatter.lineIndent

interface KotlinIndentationAdjuster {
    // ALIGN_MULTILINE_METHOD_BRACKETS
    val alignWhenMultilineFunctionParentheses: Boolean get() = false

    // ALIGN_MULTILINE_BINARY_OPERATION
    val alignWhenMultilineBinaryExpression: Boolean get() = false

    // CONTINUATION_INDENT_IN_ELVIS
    val continuationIndentInElvis: Boolean get() = false

    // CONTINUATION_INDENT_FOR_EXPRESSION_BODIES
    val continuationIndentForExpressionBodies: Boolean get() = false

    // ALIGN_MULTILINE_PARAMETERS
    val alignMultilineParameters: Boolean get() = true

    // ALIGN_MULTILINE_PARAMETERS_IN_CALLS
    val alignMultilineParametersInCalls: Boolean get() = false

    // CONTINUATION_INDENT_IN_PARAMETER_LISTS
    val continuationIndentInParameterLists: Boolean get() = false

    // CONTINUATION_INDENT_IN_ARGUMENT_LISTS
    val continuationIndentInArgumentLists: Boolean get() = false

    // CONTINUATION_INDENT_IN_IF_CONDITIONS
    val continuationIndentInIfCondition: Boolean get() = false

    // CONTINUATION_INDENT_FOR_CHAINED_CALLS
    val continuationIndentForChainedCalls: Boolean get() = false
}