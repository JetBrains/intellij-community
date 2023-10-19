// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.images.editor.paste

import com.intellij.ide.dnd.FileCopyPasteUtil
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.editor.*
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.refactoring.RefactoringBundle
import org.intellij.images.fileTypes.ImageFileTypeManager
import org.intellij.images.fileTypes.impl.SvgFileType
import org.intellij.plugins.markdown.editor.runForEachCaret
import org.intellij.plugins.markdown.images.MarkdownImagesBundle
import org.intellij.plugins.markdown.images.editor.ImageUtils
import org.intellij.plugins.markdown.lang.isMarkdownLanguage
import org.intellij.plugins.markdown.settings.MarkdownCodeInsightSettings
import java.awt.datatransfer.Transferable
import java.net.URLEncoder
import java.nio.charset.Charset
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.relativeTo

internal class MarkdownFileDropHandler: CustomFileDropHandler() {
  override fun canHandle(transferable: Transferable, editor: Editor?): Boolean {
    if (editor == null || !editor.document.isWritable) {
      return false
    }
    if (!MarkdownCodeInsightSettings.getInstance().state.enableFileDrop) {
      return false
    }
    val project = editor.project ?: return false
    val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
    return file != null && file.language.isMarkdownLanguage()
  }

  override fun handleDrop(transferable: Transferable, editor: Editor?, project: Project?): Boolean {
    if (editor == null || project == null || !editor.document.isWritable) {
      return false
    }
    val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
    if (file == null || !file.language.isMarkdownLanguage()) {
      return false
    }
    val files = FileCopyPasteUtil.getFiles(transferable)?.asSequence() ?: return false
    val content = buildTextContent(files, file)
    val document = editor.document
    runWriteAction {
      handleReadOnlyModificationException(project, document) {
        executeCommand(project, commandName) {
          editor.caretModel.runForEachCaret(reverseOrder = true) { caret ->
            document.insertString(caret.offset, content)
            caret.moveToOffset(content.length)
          }
        }
      }
    }
    return true
  }

  companion object {
    private val commandName
      get() = MarkdownImagesBundle.message("markdown.image.file.drop.handler.drop.command.name")

    internal fun buildTextContent(files: Sequence<Path>, file: PsiFile): String {
      val imageFileType = ImageFileTypeManager.getInstance().imageFileType
      val registry = FileTypeRegistry.getInstance()
      val currentDirectory = obtainDirectoryPath(file)
      val relativePaths = files.map { obtainRelativePath(it, currentDirectory) }
      return relativePaths.joinToString(separator = "\n") { path ->
        when (registry.getFileTypeByExtension(path.extension)) {
          imageFileType, SvgFileType.INSTANCE -> createImageLink(path)
          else -> createFileLink(path)
        }
      }
    }

    private fun obtainDirectoryPath(file: PsiFile): Path? {
      val directory = file.containingDirectory?.virtualFile ?: return null
      return directory.fileSystem.getNioPath(directory)
    }

    private fun obtainRelativePath(path: Path, currentDirectory: Path?): Path {
      if (currentDirectory == null) {
        return path
      }
      return path.relativeTo(currentDirectory)
    }

    private fun createUri(url: String): String {
      return URLEncoder.encode(url, Charset.defaultCharset()).replace("+", "%20")
    }

    private fun createImageLink(file: Path): String {
      return ImageUtils.createMarkdownImageText(
        description = file.name,
        path = createUri(FileUtil.toSystemIndependentName(file.toString()))
      )
    }

    private fun createFileLink(file: Path): String {
      val independentPath = createUri(FileUtil.toSystemIndependentName(file.toString()))
      return "[${file.name}]($independentPath)"
    }

    internal fun handleReadOnlyModificationException(project: Project, document: Document, block: () -> Unit) {
      try {
        block.invoke()
      } catch (exception: ReadOnlyModificationException) {
        Messages.showErrorDialog(project, exception.localizedMessage, RefactoringBundle.message("error.title"))
      } catch (exception: ReadOnlyFragmentModificationException) {
        EditorActionManager.getInstance().getReadonlyFragmentModificationHandler(document).handle(exception)
      }
    }
  }
}
