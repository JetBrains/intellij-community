package org.intellij.plugins.markdown.editor.paste

import com.intellij.ide.PasteProvider
import com.intellij.ide.dnd.FileCopyPasteUtil
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.editor.actions.PasteAction
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.psi.util.PsiEditorUtil
import org.intellij.plugins.markdown.editor.runForEachCaret
import org.intellij.plugins.markdown.lang.MarkdownLanguageUtils.isMarkdownType
import org.intellij.plugins.markdown.settings.MarkdownCodeInsightSettings
import java.awt.datatransfer.DataFlavor

internal class FileLinkPasteProvider: PasteProvider {
  override fun performPaste(dataContext: DataContext) {
    val project = dataContext.getData(CommonDataKeys.PROJECT) ?: return
    val editor = dataContext.getData(CommonDataKeys.EDITOR) ?: return
    val transferableProvider = dataContext.getData(PasteAction.TRANSFERABLE_PROVIDER) ?: return
    val transferable = transferableProvider.produce() ?: return
    val files = FileCopyPasteUtil.getFiles(transferable)?.asSequence() ?: return
    val file = PsiEditorUtil.getPsiFile(editor)
    val document = editor.document
    val content = EditorFileDropHandler.buildTextContent(files, file)
    runWriteAction {
      EditorFileDropHandler.handleReadOnlyModificationException(project, document) {
        executeCommand(project) {
          editor.caretModel.runForEachCaret(reverseOrder = true) { caret ->
            val offset = caret.offset
            document.insertString(offset, content)
            caret.moveToOffset(offset + content.length)
          }
        }
      }
    }
  }

  override fun isPasteEnabled(dataContext: DataContext): Boolean {
    if (!MarkdownCodeInsightSettings.getInstance().state.enableFilesDrop) {
      return false
    }
    val file = dataContext.getData(CommonDataKeys.VIRTUAL_FILE) ?: return false
    if (file.fileType.isMarkdownType()) {
      return CopyPasteManager.getInstance().areDataFlavorsAvailable(DataFlavor.javaFileListFlavor)
    }
    return false
  }

  override fun isPastePossible(dataContext: DataContext): Boolean {
    return true
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}
