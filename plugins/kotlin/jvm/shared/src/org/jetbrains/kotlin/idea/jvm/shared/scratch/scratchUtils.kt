// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.jvm.shared.scratch

import com.intellij.ide.scratch.ScratchFileService
import com.intellij.ide.scratch.ScratchRootType
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.messages.Topic
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.idea.actions.KOTLIN_WORKSHEET_EXTENSION
import org.jetbrains.kotlin.idea.jvm.shared.scratch.ui.KtScratchFileEditorWithPreview
import org.jetbrains.kotlin.idea.jvm.shared.scratch.ui.findScratchFileEditorWithPreview
import org.jetbrains.kotlin.parsing.KotlinParserDefinition

val LOG: Logger = Logger.getInstance("#org.jetbrains.kotlin.idea.scratch")

fun Logger.printDebugMessage(str: String) {
    if (isDebugEnabled) debug("SCRATCH: $str")
}

val VirtualFile.isKotlinWorksheet: Boolean
    get() = name.endsWith(".$KOTLIN_WORKSHEET_EXTENSION")

val VirtualFile.isKotlinScratch: Boolean
    get() = KotlinParserDefinition.STD_SCRIPT_SUFFIX == this.extension &&
        ScratchFileService.getInstance().getRootType(this) is ScratchRootType

@TestOnly
fun getScratchEditorForSelectedFile(fileManager: FileEditorManager, virtualFile: VirtualFile): KtScratchFileEditorWithPreview? {
    val editor = fileManager.getSelectedEditor(virtualFile) as? TextEditor ?: return null
    return editor.findScratchFileEditorWithPreview()
}

fun TextEditor.getScratchFile(): ScratchFile? {
    return findScratchFileEditorWithPreview()?.scratchFile
}

fun <L : Any> Project.syncPublisherWithDisposeCheck(topic: Topic<L>): L {
    return if (isDisposed) throw ProcessCanceledException() else messageBus.syncPublisher(topic)
}


fun ActionToolbar.updateToolbar() {
    ApplicationManager.getApplication().invokeLater {
        updateActionsAsync()
    }
}