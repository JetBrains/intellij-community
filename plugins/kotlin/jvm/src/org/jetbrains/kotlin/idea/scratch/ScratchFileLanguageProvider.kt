// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.scratch

import com.intellij.lang.Language
import com.intellij.lang.LanguageExtension
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.messages.Topic
import org.jetbrains.kotlin.idea.scratch.actions.ScratchCompilationSupport
import org.jetbrains.kotlin.idea.scratch.output.ScratchOutputHandlerAdapter

abstract class ScratchFileLanguageProvider {
    fun newScratchFile(project: Project, file: VirtualFile): ScratchFile? {
        val scratchFile = createFile(project, file) ?: return null

        scratchFile.replScratchExecutor = createReplExecutor(scratchFile)
        scratchFile.compilingScratchExecutor = createCompilingExecutor(scratchFile)

        scratchFile.replScratchExecutor?.addOutputHandlers()
        scratchFile.compilingScratchExecutor?.addOutputHandlers()

        scratchFile.project.syncPublisherWithDisposeCheck(ScratchFileListener.TOPIC).fileCreated(scratchFile)

        return scratchFile
    }

    private fun <L> Project.syncPublisherWithDisposeCheck(topic: Topic<L>) =
        if (isDisposed) throw ProcessCanceledException() else messageBus.syncPublisher(topic)

    private fun ScratchExecutor.addOutputHandlers() {
        addOutputHandler(object : ScratchOutputHandlerAdapter() {
            override fun onStart(file: ScratchFile) {
                ScratchCompilationSupport.start(file, this@addOutputHandlers)
            }

            override fun onFinish(file: ScratchFile) {
                ScratchCompilationSupport.stop()
            }
        })
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