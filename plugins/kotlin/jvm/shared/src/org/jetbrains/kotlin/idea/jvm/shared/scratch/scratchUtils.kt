// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.jvm.shared.scratch

import com.intellij.ide.scratch.ScratchFileService
import com.intellij.ide.scratch.ScratchRootType
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.idea.jvm.shared.scratch.ui.ScratchFileEditorWithPreview
import org.jetbrains.kotlin.idea.jvm.shared.scratch.ui.findScratchFileEditorWithPreview
import org.jetbrains.kotlin.parsing.KotlinParserDefinition

val LOG: Logger = Logger.getInstance("#org.jetbrains.kotlin.idea.scratch")

fun Logger.printDebugMessage(str: String) {
    if (isDebugEnabled) debug("SCRATCH: $str")
}

val VirtualFile.isKotlinScratch: Boolean
    get() = KotlinParserDefinition.STD_SCRIPT_SUFFIX == this.extension &&
        ScratchFileService.getInstance().getRootType(this) is ScratchRootType

@TestOnly
fun getScratchEditorForSelectedFile(fileManager: FileEditorManager, virtualFile: VirtualFile): ScratchFileEditorWithPreview? {
    val editor = fileManager.getSelectedEditor(virtualFile) as? TextEditor ?: return null
    return editor.findScratchFileEditorWithPreview()
}

fun TextEditor.getScratchFile(): ScratchFile? {
    return findScratchFileEditorWithPreview()?.scratchFile
}

fun ActionToolbar.updateToolbar() {
    ApplicationManager.getApplication().invokeLater {
        updateActionsAsync()
    }
}