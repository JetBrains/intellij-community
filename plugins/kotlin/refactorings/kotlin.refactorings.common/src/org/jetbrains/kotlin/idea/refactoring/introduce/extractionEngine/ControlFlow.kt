// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine

import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.OutputValue.ExpressionValue
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.OutputValue.Jump
import org.jetbrains.kotlin.psi.KtDeclaration


data class ControlFlow<KotlinType>(
    val outputValues: List<OutputValue<KotlinType>>,
    val boxerFactory: (List<OutputValue<KotlinType>>) -> OutputValueBoxer<KotlinType>,
    val declarationsToCopy: List<KtDeclaration>
) {
    val outputValueBoxer = boxerFactory(outputValues)

    val defaultOutputValue: ExpressionValue<KotlinType>? = outputValues.filterIsInstance<ExpressionValue<KotlinType>>().firstOrNull()

    val jumpOutputValue: Jump<KotlinType>? = outputValues.filterIsInstance<Jump<KotlinType>>().firstOrNull()
}