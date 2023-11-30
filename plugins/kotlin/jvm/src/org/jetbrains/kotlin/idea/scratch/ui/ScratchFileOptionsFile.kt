// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.scratch.ui

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.core.util.AbstractFileGistService
import org.jetbrains.kotlin.idea.scratch.ScratchFileOptions

@Service(Service.Level.PROJECT)
internal class ScratchFileOptionsFile: AbstractFileGistService<ScratchFileOptions>(
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