// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine

import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle

/**
 * Represents the [IExtractableCodeDescriptor] and if the extraction fails,
 * then status and error messages provide exact information about the failure
 */
class AnalysisResult<KotlinType>(
    val descriptor: IExtractableCodeDescriptor<KotlinType>?,
    val status: Status,
    val messages: List<ErrorMessage>
) {
    enum class Status {
        SUCCESS,
        NON_CRITICAL_ERROR,
        CRITICAL_ERROR
    }

    enum class ErrorMessage {
        NO_EXPRESSION,
        NO_CONTAINER,
        SYNTAX_ERRORS,
        SUPER_CALL,
        DENOTABLE_TYPES,
        ERROR_TYPES,
        MULTIPLE_OUTPUT,
        OUTPUT_AND_EXIT_POINT,
        MULTIPLE_EXIT_POINTS,
        DECLARATIONS_ARE_USED_OUTSIDE,
        DECLARATIONS_OUT_OF_SCOPE;

        var additionalInfo: List<String>? = null

        fun addAdditionalInfo(info: List<String>): ErrorMessage {
            additionalInfo = info
            return this
        }

        @Nls
        fun renderMessage(): String {
            val message = KotlinBundle.message(
                when (this) {
                    NO_EXPRESSION -> "cannot.refactor.no.expression"
                    NO_CONTAINER -> "cannot.refactor.no.container"
                    SYNTAX_ERRORS -> "cannot.refactor.syntax.errors"
                    SUPER_CALL -> "cannot.extract.super.call"
                    DENOTABLE_TYPES -> "parameter.types.are.not.denotable"
                    ERROR_TYPES -> "error.types.in.generated.function"
                    MULTIPLE_OUTPUT -> "selected.code.fragment.has.multiple.output.values"
                    OUTPUT_AND_EXIT_POINT -> "selected.code.fragment.has.output.values.and.exit.points"
                    MULTIPLE_EXIT_POINTS -> "selected.code.fragment.has.multiple.exit.points"
                    DECLARATIONS_ARE_USED_OUTSIDE -> "declarations.are.used.outside.of.selected.code.fragment"
                    DECLARATIONS_OUT_OF_SCOPE -> "declarations.will.move.out.of.scope"
                }
            )

            return additionalInfo?.let {
                "$message\n\n${
                    it.joinToString("\n") { msg ->
                        @Suppress("HardCodedStringLiteral")
                        StringUtil.htmlEmphasize(msg)
                    }
                }"
            } ?: message
        }
    }
}
