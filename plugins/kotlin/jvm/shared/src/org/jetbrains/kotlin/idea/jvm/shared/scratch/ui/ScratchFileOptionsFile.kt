// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.jvm.shared.scratch.ui

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.core.script.v1.AbstractFileGistService
import org.jetbrains.kotlin.idea.jvm.shared.scratch.ScratchFileOptions

@Service(Service.Level.PROJECT)
class ScratchFileOptionsFile: AbstractFileGistService<ScratchFileOptions>(
    name = "kotlin-scratch-file-options",
    version = 1,
    read = { ScratchFileOptions(readBoolean(), readBoolean(), readBoolean()) },
    write = {
        writeBoolean(it.isRepl)
        writeBoolean(it.isMakeBeforeRun)
        writeBoolean(it.isInteractiveMode)
    }
) {
    companion object {
        operator fun get(project: Project, file: VirtualFile) = project.service<ScratchFileOptionsFile>()[file]

        operator fun set(project: Project, file: VirtualFile, newValue: ScratchFileOptions?) {
            project.service<ScratchFileOptionsFile>()[file] = newValue
        }
    }
}