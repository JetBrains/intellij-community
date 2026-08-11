// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.searching.usages

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.resolution.KaCall
import org.jetbrains.kotlin.analysis.api.resolution.calls
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.psi.KtElement

internal inline fun <R> withResolvedCall(
    element: KtElement,
    crossinline block: context(KaSession) (KaCall) -> R
): R? = analyze(element) {
    element.resolveToCall()?.calls?.singleOrNull()?.let { block(it) }
}
