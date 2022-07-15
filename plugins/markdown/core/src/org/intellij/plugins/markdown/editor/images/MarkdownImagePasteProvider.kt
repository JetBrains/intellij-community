// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.images

import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.editor.*
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.vfs.VirtualFile
import org.intellij.images.ide.EditorImagePasteProvider
import org.intellij.plugins.markdown.lang.MarkdownFileType

internal class MarkdownImagePasteProvider: EditorImagePasteProvider() {
  override val supportedFileType: FileType
    get() = MarkdownFileType.INSTANCE

  override fun Editor.imageFilePasted(imageFile: VirtualFile) {
    val pastedFileName = imageFile.name
    try {
      executeCommand {
        caretModel.runForEachCaret(reverseOrder = true) { caret ->
          val offset = caret.offset
          val textToInsert = ImageUtils.createMarkdownImageText(pastedFileName, pastedFileName)
          document.insertString(offset, textToInsert)
          caretModel.moveToOffset(offset + textToInsert.length)
        }
      }
    } catch (e: ReadOnlyModificationException) {
      // do nothing
    } catch (e: ReadOnlyFragmentModificationException) {
      // do nothing
    }
  }

  companion object {
    private fun CaretModel.runForEachCaret(reverseOrder: Boolean = false, block: (Caret) -> Unit) {
      runForEachCaret(block, reverseOrder)
    }
  }
}
