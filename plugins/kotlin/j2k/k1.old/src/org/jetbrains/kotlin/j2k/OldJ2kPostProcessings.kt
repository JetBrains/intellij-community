// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k

import com.intellij.openapi.components.service
import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.resolve.diagnostics.Diagnostics

@K1Deprecation
interface OldJ2kPostProcessing {
    fun createAction(element: KtElement, diagnostics: Diagnostics): (() -> Unit)?
    val writeActionNeeded: Boolean
}

@K1Deprecation
interface OldJ2KPostProcessingRegistrar {
    val processings: Collection<OldJ2kPostProcessing>

    fun priority(processing: OldJ2kPostProcessing): Int

    companion object {
        val instance: OldJ2KPostProcessingRegistrar
            get() = service()
    }
}