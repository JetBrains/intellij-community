// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.images.editor.paste

import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.edtWriteAction
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
import java.io.File
import java.net.URLEncoder
import java.nio.charset.Charset
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.relativeTo

internal class MarkdownFileDropHandler : FileDropHandler {

  override suspend fun handleDrop(e: FileDropEvent): Boolean {
    if (e.editor == null) return false

    if (!readAction { canHandle(e.project, e.editor!!) }) return false

    return handleDrop(e.project, e.files, e.editor!!)
  }

  private fun canHandle(project: Project, editor: Editor): Boolean {
    if (editor.isDisposed) return false

    if (!editor.document.isWritable) {
      return false
    }
    if (!MarkdownCodeInsightSettings.getInstance().state.enableFileDrop) {
      return false
    }

    val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
    return !(file == null || !file.language.isMarkdownLanguage())
  }

  private suspend fun handleDrop(project: Project, files: Collection<File>, editor: Editor): Boolean {
    return edtWriteAction {
      if (editor.isDisposed) return@edtWriteAction false

      val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
      if (file == null || !file.language.isMarkdownLanguage()) {
        return@edtWriteAction false
      }

      val content = Manager.buildTextContent(files.map { it.toPath() }.asSequence(), file)
      val document = editor.document

      Manager.handleReadOnlyModificationException(project, document) {
        executeCommand(project, Manager.commandName) {
          editor.caretModel.runForEachCaret(reverseOrder = true) { caret ->
            document.insertString(caret.offset, content)
            caret.moveToOffset(content.length)
          }
        }
      }
      true
    }
  }

  internal object Manager {
    val commandName
      get() = MarkdownImagesBundle.message("markdown.image.file.drop.handler.drop.command.name")

    fun buildTextContent(files: Sequence<Path>, file: PsiFile): String {
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
      return FileUtil.splitPath(FileUtil.toSystemDependentName(url)).joinToString("/") { URLEncoder.encode(it, Charset.defaultCharset()).replace("+", "%20") }
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

    fun handleReadOnlyModificationException(project: Project, document: Document, block: () -> Unit) {
      try {
        block.invoke()
      }
      catch (exception: ReadOnlyModificationException) {
        Messages.showErrorDialog(project, exception.localizedMessage, RefactoringBundle.message("error.title"))
      }
      catch (exception: ReadOnlyFragmentModificationException) {
        EditorActionManager.getInstance().getReadonlyFragmentModificationHandler(document).handle(exception)
      }
    }
  }
}
