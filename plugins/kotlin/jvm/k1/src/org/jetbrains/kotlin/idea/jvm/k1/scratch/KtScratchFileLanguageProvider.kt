// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.jvm.k1.scratch

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.jvm.k1.scratch.compile.KtCompilingExecutor
import org.jetbrains.kotlin.idea.jvm.k1.scratch.repl.KtScratchReplExecutor
import org.jetbrains.kotlin.idea.jvm.shared.scratch.ScratchFile

class KtScratchFileLanguageProvider : ScratchFileLanguageProvider() {
    override fun createFile(project: Project, file: VirtualFile): ScratchFile = K1KotlinScratchFile(project, file)
    override fun createReplExecutor(file: ScratchFile): KtScratchReplExecutor = KtScratchReplExecutor(file as K1KotlinScratchFile)
    override fun createCompilingExecutor(file: ScratchFile): KtCompilingExecutor = KtCompilingExecutor(file as K1KotlinScratchFile)
}