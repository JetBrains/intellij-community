// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.inline.codeInliner

import org.jetbrains.kotlin.idea.references.readWriteAccess
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.resolve.references.ReferenceAccess

abstract class AbstractPropertyUsageReplacementStrategy(readReplacement: CodeToInline?, writeReplacement: CodeToInline?) {
    private val readReplacementStrategy = readReplacement?.let {
        createCallableStrategy(it, inlineSetter = false)
    }
    private val writeReplacementStrategy = writeReplacement?.let {
        createCallableStrategy(it, inlineSetter = true)
    }

    protected abstract fun createCallableStrategy(codeToInline: CodeToInline, inlineSetter: Boolean): UsageReplacementStrategy
    fun createReplacer(usage: KtReferenceExpression): (() -> KtElement?)? {
        return when (usage.readWriteAccess(useResolveForReadWrite = true)) {
            ReferenceAccess.READ -> readReplacementStrategy?.createReplacer(usage)
            ReferenceAccess.WRITE -> writeReplacementStrategy?.createReplacer(usage)
            ReferenceAccess.READ_WRITE -> null
        }
    }
}