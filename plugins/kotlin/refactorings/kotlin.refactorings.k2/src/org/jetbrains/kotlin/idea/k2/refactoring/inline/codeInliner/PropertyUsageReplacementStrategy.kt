// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.inline.codeInliner

import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.AbstractPropertyUsageReplacementStrategy
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.CodeToInline
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.UsageReplacementStrategy

class PropertyUsageReplacementStrategy(readReplacement: CodeToInline?, writeReplacement: CodeToInline?) : UsageReplacementStrategy,
                                                                                                          AbstractPropertyUsageReplacementStrategy(
                                                                                                              readReplacement,
                                                                                                              writeReplacement
                                                                                                          ) {
    override fun createCallableStrategy(codeToInline: CodeToInline, inlineSetter: Boolean) =
        CallableUsageReplacementStrategy(codeToInline, inlineSetter = inlineSetter)

}