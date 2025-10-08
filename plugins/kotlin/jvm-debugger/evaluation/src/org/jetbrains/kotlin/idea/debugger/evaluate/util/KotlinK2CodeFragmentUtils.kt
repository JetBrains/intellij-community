// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.evaluate.util

import com.intellij.openapi.util.Key
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypePointer
import org.jetbrains.kotlin.psi.KtExpression

object KotlinK2CodeFragmentUtils {
    @OptIn(KaExperimentalApi::class)
    val RUNTIME_TYPE_EVALUATOR_K2: Key<(KtExpression) -> KaTypePointer<KaType>?> = Key.create("RUNTIME_TYPE_EVALUATOR_K2")
}