// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.test

import org.jetbrains.kotlin.config.JvmClosureGenerationScheme

abstract class AbstractIndyLambdaKotlinEvaluateExpressionTest : AbstractKotlinEvaluateExpressionTest() {
    override fun useIrBackend(): Boolean = true
    override fun fragmentCompilerBackend() =
        FragmentCompilerBackend.JVM_IR

    override fun lambdasGenerationScheme() = JvmClosureGenerationScheme.INDY
}
