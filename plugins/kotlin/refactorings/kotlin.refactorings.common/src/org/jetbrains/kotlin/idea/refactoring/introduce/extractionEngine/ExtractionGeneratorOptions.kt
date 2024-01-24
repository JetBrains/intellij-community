// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine

/**
 * @property inTempFile Boolean indicating whether to extract the code into a temporary file.
 * If extracted to temp file, duplicates won't be searched, so validation can be made faster
 *
 * @property allowExpressionBody If true, refactoring would try to collapse function block body to expression.
 * Because changing user code is undesired e.g., extract parameter should keep this option as false
 *
 * @property delayInitialOccurrenceReplacement if true, extracted declaration would be renamed when inserted TODO reconsider fix for KTIJ-4200
 *
 * @property isConst true for extract constant refactoring, false otherwise
 */
data class ExtractionGeneratorOptions(
    val inTempFile: Boolean = false,
    val target: ExtractionTarget = ExtractionTarget.FUNCTION,
    val allowExpressionBody: Boolean = true,
    val delayInitialOccurrenceReplacement: Boolean = false,
    val isConst: Boolean = false
) {
    companion object {
        @JvmField
        val DEFAULT = ExtractionGeneratorOptions()
    }
}