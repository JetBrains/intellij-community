// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.images.editor.paste

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.FileDropEvent
import com.intellij.openapi.editor.FileDropHandler
import com.intellij.openapi.editor.ReadOnlyFragmentModificationException
import com.intellij.openapi.editor.ReadOnlyModificationException
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.RefactoringBundle
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.intellij.images.fileTypes.ImageFileTypeManager
import org.intellij.images.fileTypes.impl.SvgFileType
import org.intellij.plugins.markdown.editor.runForEachCaret
import org.intellij.plugins.markdown.images.MarkdownImagesBundle
import org.intellij.plugins.markdown.images.editor.ImageUtils
import org.intellij.plugins.markdown.lang.isMarkdownLanguage
import org.intellij.plugins.markdown.lang.isMarkdownType
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownHeader
import org.intellij.plugins.markdown.settings.MarkdownCodeInsightSettings
import org.jetbrains.annotations.VisibleForTesting
import java.io.File
import java.net.URLEncoder
import java.nio.charset.Charset
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.relativeTo

@VisibleForTesting
class MarkdownFileDropHandler : FileDropHandler {

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
    val content = readAction {
      val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
      if (file == null || !file.language.isMarkdownLanguage()) {
        return@readAction null
      }
      Manager.buildTextContent(files.map { it.toPath() }.asSequence(), file)
    } ?: return false

    return edtWriteAction {
      if (editor.isDisposed) return@edtWriteAction false

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

  object Manager {
    val commandName
      get() = MarkdownImagesBundle.message("markdown.image.file.drop.handler.drop.command.name")

    @RequiresBackgroundThread
    @RequiresReadLock
    fun buildTextContent(files: Sequence<Path>, file: PsiFile): String {
      val imageFileType = ImageFileTypeManager.getInstance().imageFileType
      val registry = FileTypeRegistry.getInstance()
      val currentDirectory = obtainDirectoryPath(file)
      return files.joinToString(separator = "\n") { path ->
        val relativePath = obtainRelativePath(path, currentDirectory)
        val fileType = registry.getFileTypeByExtension(path.extension)
        when {
          fileType == imageFileType || fileType == SvgFileType.INSTANCE -> createImageLink(relativePath)
          fileType.isMarkdownType() -> createFileLink(relativePath, findFirstHeaderText(path, file.project) ?: relativePath.name)
          else -> createFileLink(relativePath, relativePath.name)
        }
      }
    }

    private fun findFirstHeaderText(path: Path, project: Project): String? {
      val virtualFile = VirtualFileManager.getInstance().findFileByNioPath(path) ?: return null
      val markdownFile = PsiManager.getInstance(project).findFile(virtualFile) as? MarkdownFile ?: return null
      val header = PsiTreeUtil.findChildrenOfType(markdownFile, MarkdownHeader::class.java).firstOrNull { it.level == 1 } ?: return null
      val text = header.buildVisibleText() ?: return null
      return text.replace(linkTextEscapeRegex) { match ->
        when (match.value) {
          "\\", "[", "]" -> "\\" + match.value
          else -> match.value
        }
      }
    }

    private val linkTextEscapeRegex = Regex("""`[^`]*+`|\\.?|[\[\]]""")

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

    private fun createFileLink(file: Path, text: String): String {
      val independentPath = createUri(FileUtil.toSystemIndependentName(file.toString()))
      return "[$text]($independentPath)"
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
