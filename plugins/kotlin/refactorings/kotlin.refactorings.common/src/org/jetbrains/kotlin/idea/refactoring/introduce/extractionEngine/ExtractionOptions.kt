// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine

data class ExtractionOptions(
    val inferUnitTypeForUnusedValues: Boolean = true,
    val enableListBoxing: Boolean = false,
    val extractAsProperty: Boolean = false,
    val captureLocalFunctions: Boolean = false,
    val canWrapInWith: Boolean = false
) {
    companion object {
        val DEFAULT: ExtractionOptions = ExtractionOptions()
    }
}
