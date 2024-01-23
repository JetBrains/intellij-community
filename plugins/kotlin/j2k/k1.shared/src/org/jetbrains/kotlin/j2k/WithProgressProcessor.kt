// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.j2k

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface WithProgressProcessor {
    fun <TInputItem, TOutputItem> processItems(
        fractionPortion: Double,
        inputItems: Iterable<TInputItem>,
        processItem: (TInputItem) -> TOutputItem
    ): List<TOutputItem>

    fun updateState(fileIndex: Int?, phase: Int, description: String)

    fun updateState(phase: Int, subPhase: Int, subPhaseCount: Int, fileIndex: Int?, description: String)

    fun <T> process(action: () -> T): T
}