// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.core

import com.intellij.openapi.components.serviceOrNull
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface KotlinDebuggerLegacyFacade {
    val editorTextProvider: KotlinEditorTextProvider

    companion object {
        @JvmStatic
        fun getInstance(): KotlinDebuggerLegacyFacade? = serviceOrNull()
    }
}