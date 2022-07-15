// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.images

import com.intellij.ide.dnd.FileCopyPasteUtil
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.editor.*
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.refactoring.RefactoringBundle
import org.intellij.images.fileTypes.ImageFileTypeManager
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.util.MarkdownFileUtil
import java.awt.datatransfer.Transferable
import kotlin.io.path.extension

internal class MarkdownImageFileDropHandler: CustomFileDropHandler() {
  override fun canHandle(transferable: Transferable, editor: Editor?): Boolean {
    if (editor == null || !editor.document.isWritable) {
      return false
    }
    val files = FileCopyPasteUtil.getFiles(transferable) ?: return false
    val imageFileType = ImageFileTypeManager.getInstance().imageFileType
    val registry = FileTypeRegistry.getInstance()
    return files.map { it.extension }.all { registry.getFileTypeByExtension(it) == imageFileType }
  }

  override fun handleDrop(transferable: Transferable, editor: Editor?, project: Project?): Boolean {
    if (editor == null || !editor.document.isWritable) {
      return false
    }
    val files = FileCopyPasteUtil.getFileList(transferable) ?: return false
    val document = editor.document
    val currentDirectory = MarkdownFileUtil.getDirectory(project, document)
    val content = files.joinToString(separator = "\n") {
      ImageUtils.createMarkdownImageText(path = MarkdownFileUtil.getPathForMarkdownImage(it.toString(), currentDirectory))
    }
    runWriteAction {
      try {
        executeCommand(project, commandName) {
          editor.caretModel.runForEachCaret(reverseOrder = true) { caret ->
            document.insertString(caret.offset, content)
            caret.moveToOffset(content.length)
          }
        }
      } catch (exception: ReadOnlyModificationException) {
        Messages.showErrorDialog(project, exception.localizedMessage, RefactoringBundle.message("error.title"))
      } catch (exception: ReadOnlyFragmentModificationException) {
        EditorActionManager.getInstance().getReadonlyFragmentModificationHandler(document).handle(exception)
      }
    }
    return true
  }

  companion object {
    private val commandName
      get() = MarkdownBundle.message("markdown.image.file.drop.handler.drop.command.name")

      private fun CaretModel.runForEachCaret(reverseOrder: Boolean = false, block: (Caret) -> Unit) {
        runForEachCaret(block, reverseOrder)
      }
    }
}
