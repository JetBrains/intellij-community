// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.test

import org.jetbrains.kotlin.config.JvmClosureGenerationScheme

abstract class AbstractK1IdeK2CodeKotlinEvaluateExpressionTest : AbstractK1IrKotlinEvaluateExpressionTest() {
    override val compileWithK2 = true

    override fun lambdasGenerationScheme() = JvmClosureGenerationScheme.INDY
}