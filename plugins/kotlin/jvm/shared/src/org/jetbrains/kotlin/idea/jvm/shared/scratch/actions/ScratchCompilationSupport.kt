// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.jvm.shared.scratch.actions

import org.jetbrains.kotlin.idea.jvm.shared.scratch.ScratchExecutor
import org.jetbrains.kotlin.idea.jvm.shared.scratch.ScratchFile

object ScratchCompilationSupport {
    private data class FileExecutor(val file: ScratchFile, val executor: ScratchExecutor)
    @Volatile
    private var fileExecutor: FileExecutor? = null

    fun isInProgress(file: ScratchFile): Boolean = fileExecutor?.file == file
    fun isAnyInProgress(): Boolean = fileExecutor != null

    fun start(file: ScratchFile, executor: ScratchExecutor) {
        fileExecutor = FileExecutor(file, executor)
    }

    fun stop() {
        fileExecutor = null
    }

    fun forceStop() {
        fileExecutor?.executor?.stop()

        stop()
    }
}