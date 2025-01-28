// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.scratch

import com.intellij.lang.Language
import com.intellij.lang.LanguageExtension
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.messages.Topic
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.kotlin.idea.scratch.actions.ScratchCompilationSupport
import org.jetbrains.kotlin.idea.scratch.output.ScratchOutputHandlerAdapter

abstract class ScratchFileLanguageProvider {
    fun newScratchFile(project: Project, file: VirtualFile, scope: CoroutineScope): ScratchFile? {
        val scratchFile = createFile(project, file) ?: return null

        scratchFile.replScratchExecutor = createReplExecutor(scratchFile)
        scratchFile.compilingScratchExecutor = createCompilingExecutor(scratchFile)
        scratchFile.k2ScratchExecutor = K2ScratchExecutor(scratchFile, project, scope)

        scratchFile.replScratchExecutor?.addOutputHandlers()
        scratchFile.compilingScratchExecutor?.addOutputHandlers()
        scratchFile.k2ScratchExecutor?.addOutputHandlers()

        scratchFile.project.syncPublisherWithDisposeCheck(ScratchFileListener.TOPIC).fileCreated(scratchFile)

        return scratchFile
    }

    private fun <L : Any> Project.syncPublisherWithDisposeCheck(topic: Topic<L>): L {
        return if (isDisposed) throw ProcessCanceledException() else messageBus.syncPublisher(topic)
    }

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