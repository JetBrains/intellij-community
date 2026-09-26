// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.images.editor.paste

import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ReadOnlyFragmentModificationException
import com.intellij.openapi.editor.ReadOnlyModificationException
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.vfs.VirtualFile
import org.intellij.images.ide.EditorImagePasteProvider
import org.intellij.plugins.markdown.editor.runForEachCaret
import org.intellij.plugins.markdown.images.editor.ImageUtils
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
    } catch (exception: ReadOnlyModificationException) {
      // do nothing
    } catch (exception: ReadOnlyFragmentModificationException) {
      // do nothing
    }
  }
}
