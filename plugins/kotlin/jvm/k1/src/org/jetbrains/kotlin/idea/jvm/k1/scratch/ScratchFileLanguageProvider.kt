// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.jvm.k1.scratch

import com.intellij.lang.Language
import com.intellij.lang.LanguageExtension
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.jvm.shared.scratch.ScratchExecutor
import org.jetbrains.kotlin.idea.jvm.shared.scratch.ScratchFile
import org.jetbrains.kotlin.idea.jvm.shared.scratch.ScratchFileListener
import org.jetbrains.kotlin.idea.jvm.shared.scratch.syncPublisherWithDisposeCheck

abstract class ScratchFileLanguageProvider {
    fun newScratchFile(project: Project, file: VirtualFile): ScratchFile? {
        val scratchFile = createFile(project, file) as? K1KotlinScratchFile ?: return null

        scratchFile.replScratchExecutor = createReplExecutor(scratchFile)
        scratchFile.compilingScratchExecutor = createCompilingExecutor(scratchFile)

        scratchFile.project.syncPublisherWithDisposeCheck(ScratchFileListener.TOPIC).fileCreated(scratchFile)

        return scratchFile
    }

    protected abstract fun createFile(project: Project, file: VirtualFile): ScratchFile?
    protected abstract fun createReplExecutor(file: ScratchFile): SequentialScratchExecutor?
    protected abstract fun createCompilingExecutor(file: ScratchFile): ScratchExecutor?

    companion object {
        private val EXTENSION = LanguageExtension<ScratchFileLanguageProvider>("org.jetbrains.kotlin.scratchFileLanguageProvider")

        fun get(language: Language): ScratchFileLanguageProvider? {
            return EXTENSION.forLanguage(language)
        }

        fun get(fileType: FileType): ScratchFileLanguageProvider? {
            return (fileType as? LanguageFileType)?.language?.let { get(it) }
        }
    }
}
