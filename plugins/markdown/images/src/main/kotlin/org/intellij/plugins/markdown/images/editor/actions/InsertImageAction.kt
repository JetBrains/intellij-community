// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.images.editor.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.ReadOnlyFragmentModificationException
import com.intellij.openapi.editor.ReadOnlyModificationException
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import com.intellij.refactoring.RefactoringBundle
import org.intellij.plugins.markdown.images.MarkdownImagesBundle
import org.intellij.plugins.markdown.images.editor.ConfigureImageDialog
import org.intellij.plugins.markdown.images.editor.ImageUtils
import org.intellij.plugins.markdown.images.editor.MarkdownImageData
import org.intellij.plugins.markdown.ui.actions.MarkdownActionPlaces
import org.intellij.plugins.markdown.ui.actions.MarkdownActionUtil

internal class InsertImageAction: DumbAwareAction() {
  init {
    addTextOverride(MarkdownActionPlaces.INSERT_POPUP) {
      MarkdownImagesBundle.message("action.org.intellij.plugins.markdown.ui.actions.styling.InsertImageAction.insert.popup.text")
    }
  }

  override fun update(event: AnActionEvent) {
    val editor = MarkdownActionUtil.findMarkdownEditor(event, strictMarkdown = true)
    event.presentation.apply {
      isVisible = editor != null
      isEnabled = editor?.document?.isWritable == true
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun actionPerformed(event: AnActionEvent) {
    val editor = MarkdownActionUtil.findMarkdownEditor(event, strictMarkdown = true) ?: return
    val project = editor.project
    ConfigureImageDialog(project, MarkdownImagesBundle.message("markdown.insert.image.dialog.title")).show { imageData ->
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
