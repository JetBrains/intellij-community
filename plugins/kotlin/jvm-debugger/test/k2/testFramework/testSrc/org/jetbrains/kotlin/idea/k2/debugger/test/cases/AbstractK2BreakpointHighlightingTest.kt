// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.debugger.test.cases

import org.jetbrains.kotlin.config.JvmClosureGenerationScheme
import org.jetbrains.kotlin.idea.debugger.test.AbstractIrBreakpointHighlightingTest

abstract class AbstractK2IdeK1CodeBreakpointHighlightingTest : AbstractIrBreakpointHighlightingTest()

abstract class AbstractK2IdeK2CodeBreakpointHighlightingTest : AbstractK2IdeK1CodeBreakpointHighlightingTest() {
    override val compileWithK2 = true
    override fun lambdasGenerationScheme() = JvmClosureGenerationScheme.INDY
}
