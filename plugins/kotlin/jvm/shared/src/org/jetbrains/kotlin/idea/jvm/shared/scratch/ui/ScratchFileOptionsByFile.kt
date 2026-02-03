// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.jvm.shared.scratch.ui

import com.intellij.openapi.components.Service
import org.jetbrains.kotlin.idea.core.script.v1.AbstractFileGistService

@Service(Service.Level.PROJECT)
class ScratchFileOptionsByFile: AbstractFileGistService<ScratchFileOptions>(
    name = "kotlin-scratch-file-options",
    version = 1,
    read = { ScratchFileOptions(readBoolean(), readBoolean(), readBoolean()) },
    write = {
        writeBoolean(it.isRepl)
        writeBoolean(it.isMakeBeforeRun)
        writeBoolean(it.isInteractiveMode)
    }
)

data class ScratchFileOptions(
    val isRepl: Boolean = false,
    val isMakeBeforeRun: Boolean = true,
    val isInteractiveMode: Boolean = false
)
