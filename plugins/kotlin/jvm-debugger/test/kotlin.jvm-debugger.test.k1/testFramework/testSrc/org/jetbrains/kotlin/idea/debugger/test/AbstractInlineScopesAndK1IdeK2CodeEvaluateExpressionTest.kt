// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.test

abstract class AbstractInlineScopesAndK1IdeK2CodeEvaluateExpressionTest
    : AbstractK1IdeK2CodeKotlinEvaluateExpressionTest()
{
    override val useInlineScopes = true
}