// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.scratch

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.scratch.compile.KtCompilingExecutor
import org.jetbrains.kotlin.idea.scratch.repl.KtScratchReplExecutor

class KtScratchFileLanguageProvider : ScratchFileLanguageProvider() {
    override fun createFile(project: Project, file: VirtualFile): ScratchFile = KtScratchFile(project, file)
    override fun createReplExecutor(file: ScratchFile): KtScratchReplExecutor = KtScratchReplExecutor(file)
    override fun createCompilingExecutor(file: ScratchFile): KtCompilingExecutor = KtCompilingExecutor(file)
}