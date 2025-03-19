// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k

data class Result(
    val results: List<ElementResult?>,
    val externalCodeProcessing: ExternalCodeProcessing?,
    val converterContext: ConverterContext?
) {
    companion object {
        val EMPTY = Result(results = emptyList(), externalCodeProcessing = null, converterContext = null)
    }
}