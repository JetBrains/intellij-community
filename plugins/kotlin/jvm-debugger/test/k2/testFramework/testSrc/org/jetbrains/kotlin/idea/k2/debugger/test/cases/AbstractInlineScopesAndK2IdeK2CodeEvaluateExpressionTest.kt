// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.debugger.test.cases

import org.jetbrains.kotlin.config.JvmClosureGenerationScheme
import org.jetbrains.kotlin.idea.debugger.test.AbstractIrKotlinEvaluateExpressionWithIRFragmentCompilerTest

abstract class AbstractInlineScopesAndK2IdeK2CodeEvaluateExpressionTest :
    AbstractIrKotlinEvaluateExpressionWithIRFragmentCompilerTest()
{

    override val compileWithK2 = true

    override fun lambdasGenerationScheme() = JvmClosureGenerationScheme.INDY

    override val useInlineScopes = true
}
