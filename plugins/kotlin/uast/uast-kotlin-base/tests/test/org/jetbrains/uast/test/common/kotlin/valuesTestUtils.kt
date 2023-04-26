// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.uast.test.common.kotlin

import org.jetbrains.uast.UFile
import org.jetbrains.uast.evaluation.UEvaluatorExtension
import org.jetbrains.uast.evaluation.analyzeAll

fun UFile.asLogValues(
    uEvaluatorExtension: UEvaluatorExtension? = null
): String {
    val evaluationContext = analyzeAll(extensions = uEvaluatorExtension?.let { listOf(it) } ?: emptyList())
    return ValueLogger(evaluationContext).apply {
        this@asLogValues.accept(this)
    }.toString()
}
