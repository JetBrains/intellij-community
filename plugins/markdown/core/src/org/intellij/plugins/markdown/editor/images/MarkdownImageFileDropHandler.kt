// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.images

import com.intellij.ide.dnd.FileCopyPasteUtil
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.CustomFileDropHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ReadOnlyFragmentModificationException
import com.intellij.openapi.editor.ReadOnlyModificationException
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.refactoring.RefactoringBundle
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.util.MarkdownFileUtil
import java.awt.datatransfer.Transferable
import java.io.File

internal class MarkdownImageFileDropHandler : CustomFileDropHandler() {
  override fun canHandle(transferable: Transferable, editor: Editor?): Boolean {
    if (editor?.document?.isWritable != true) {
      return false
    }
    val droppedFiles = FileCopyPasteUtil.getFileList(transferable) ?: return false
    return droppedFiles.all(::isImage)
  }

  override fun handleDrop(transferable: Transferable, editor: Editor?, project: Project?): Boolean {
    if (editor?.document?.isWritable != true) {
      return false
    }
    val droppedFiles = FileCopyPasteUtil.getFileList(transferable) ?: return false
    if (!droppedFiles.all(::isImage)) {
      return false
    }
    val document = editor.document
    val currentDirectory = MarkdownFileUtil.getDirectory(project, document)
    val caret = editor.caretModel.currentCaret
    try {
      val commandName = MarkdownBundle.message("markdown.image.file.drop.handler.drop.command.name")
      WriteCommandAction.runWriteCommandAction(project, commandName, null, {
        for (droppedFile in droppedFiles) {
          val imageText = ImageUtils.createMarkdownImageText(
            path = MarkdownFileUtil.getPathForMarkdownImage(droppedFile.path, currentDirectory)
          )
          document.insertString(caret.offset, imageText)
          caret.moveToOffset(caret.offset + imageText.length)
        }
      })
    } catch (exception: ReadOnlyModificationException) {
      Messages.showErrorDialog(project, exception.localizedMessage, RefactoringBundle.message("error.title"))
    } catch (exception: ReadOnlyFragmentModificationException) {
      EditorActionManager.getInstance().getReadonlyFragmentModificationHandler(document).handle(exception)
    }
    return true
  }

  companion object {
    private val IMAGE_EXTENSIONS = setOf("bmp", "gif", "jpeg", "jpg", "png", "webp")

    private fun isImage(file: File): Boolean {
      return file.extension in IMAGE_EXTENSIONS
    }
  }
}
