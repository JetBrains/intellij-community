// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.conversions

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.nj2k.*
import org.jetbrains.kotlin.nj2k.tree.*
import org.jetbrains.kotlin.nj2k.tree.Mutability.UNKNOWN

class InferMutabilityOfLocalVariablesConversion(context: NewJ2kConverterContext) : RecursiveConversion(context) {
    context(KtAnalysisSession)
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        if (element !is JKLocalVariable || element.mutability != UNKNOWN) return recurse(element)
        val scope = element.parentOfType<JKMethod>() ?: element.parentOfType<JKFile>() ?: return recurse(element)
        element.mutability = element.inferMutabilityFromWritableUsages(scope, context)
        return recurse(element)
    }
}