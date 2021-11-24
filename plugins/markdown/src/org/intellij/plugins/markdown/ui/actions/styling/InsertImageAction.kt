// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.actions.styling

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.ReadOnlyFragmentModificationException
import com.intellij.openapi.editor.ReadOnlyModificationException
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.Messages
import com.intellij.refactoring.RefactoringBundle
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.editor.images.ConfigureImageDialog
import org.intellij.plugins.markdown.editor.images.ImageUtils
import org.intellij.plugins.markdown.editor.images.MarkdownImageData
import org.intellij.plugins.markdown.ui.actions.MarkdownActionPlaces
import org.intellij.plugins.markdown.ui.actions.MarkdownActionUtil

class InsertImageAction : AnAction(), DumbAware {
  init {
    addTextOverride(MarkdownActionPlaces.INSERT_POPUP) {
      MarkdownBundle.message("action.org.intellij.plugins.markdown.ui.actions.styling.InsertImageAction.insert.popup.text")
    }
  }

  override fun update(event: AnActionEvent) {
    event.presentation.isEnabled = MarkdownActionUtil.findMarkdownTextEditor(event)?.document?.isWritable == true
  }

  override fun actionPerformed(event: AnActionEvent) {
    val editor = MarkdownActionUtil.findMarkdownTextEditor(event) ?: return
    val project = editor.project
    ConfigureImageDialog(project, MarkdownBundle.message("markdown.insert.image.dialog.title")).show { imageData ->
      val document = editor.document
      val imageText = buildImageText(imageData)
      try {
        WriteCommandAction.runWriteCommandAction(project, templateText, null, {
          editor.caretModel.runForEachCaret { caret ->
            val offset = caret.offset
            document.insertString(offset, imageText)
            caret.moveToOffset(offset + imageText.length)
          }
        })
      }
      catch (exception: ReadOnlyModificationException) {
        Messages.showErrorDialog(project, exception.localizedMessage, RefactoringBundle.message("error.title"))
      }
      catch (exception: ReadOnlyFragmentModificationException) {
        EditorActionManager.getInstance().getReadonlyFragmentModificationHandler(document).handle(exception)
      }
    }
  }

  private fun buildImageText(imageData: MarkdownImageData): String {
    return when {
      imageData.shouldConvertToHtml -> {
        ImageUtils.createHtmlImageText(imageData)
      }
      else -> {
        val (path, _, _, title, description) = imageData
        ImageUtils.createMarkdownImageText(description, path, title)
      }
    }
  }
}
