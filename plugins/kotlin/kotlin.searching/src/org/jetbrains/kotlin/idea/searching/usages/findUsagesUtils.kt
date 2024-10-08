// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.searching.usages

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.KaCall
import org.jetbrains.kotlin.analysis.api.resolution.calls
import org.jetbrains.kotlin.psi.KtElement

internal inline fun <R> withResolvedCall(element: KtElement, crossinline block: KaSession.(KaCall) -> R): R? {
    return analyze(element) {
        element.resolveToCall()?.calls?.singleOrNull()?.let { block(it) }
    }
}
