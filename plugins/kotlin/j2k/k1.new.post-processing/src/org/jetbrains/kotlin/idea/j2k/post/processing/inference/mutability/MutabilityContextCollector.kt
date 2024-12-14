// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.j2k.post.processing.inference.mutability

import org.jetbrains.kotlin.idea.j2k.post.processing.inference.common.ClassReference
import org.jetbrains.kotlin.idea.j2k.post.processing.inference.common.ContextCollector
import org.jetbrains.kotlin.idea.j2k.post.processing.inference.common.State
import org.jetbrains.kotlin.idea.j2k.post.processing.inference.common.hasUnknownLabel
import org.jetbrains.kotlin.idea.resolve.ResolutionFacade
import org.jetbrains.kotlin.j2k.ConverterContext
import org.jetbrains.kotlin.psi.KtTypeElement

class MutabilityContextCollector(
    resolutionFacade: ResolutionFacade,
    private val converterContext: ConverterContext
) : ContextCollector(resolutionFacade) {
    override fun ClassReference.getState(typeElement: KtTypeElement?): State = when {
        typeElement == null -> State.UNUSED
        typeElement.hasUnknownLabel(converterContext) { it.unknownMutability } -> State.UNKNOWN
        else -> State.UNUSED
    }
}