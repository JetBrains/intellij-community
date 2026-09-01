// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.searching.usages

import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.KaSimpleCall
import org.jetbrains.kotlin.analysis.api.resolution.calls
import org.jetbrains.kotlin.analysis.api.resolution.resolveSuccessfulCall
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.resolution.KtResolvableCall

@OptIn(KaExperimentalApi::class)
internal inline fun <R> withResolvedCall(
    element: KtElement,
    crossinline block: context(KaSession) (KaSimpleCall<*, *>) -> R
): R? = analyze(element) {
    (element as? KtResolvableCall)?.resolveSuccessfulCall()?.calls?.singleOrNull()?.let { block(it) }
}
