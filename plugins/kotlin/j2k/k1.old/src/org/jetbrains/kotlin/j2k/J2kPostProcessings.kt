// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k

import com.intellij.openapi.components.service
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.resolve.diagnostics.Diagnostics

interface J2kPostProcessing {
    fun createAction(element: KtElement, diagnostics: Diagnostics): (() -> Unit)?
    val writeActionNeeded: Boolean
}

interface J2KPostProcessingRegistrar {
    val processings: Collection<J2kPostProcessing>

    fun priority(processing: J2kPostProcessing): Int

    companion object {
        val instance: J2KPostProcessingRegistrar
            get() = service()
    }
}