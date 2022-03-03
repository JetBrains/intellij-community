// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.images

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ReadOnlyFragmentModificationException
import com.intellij.openapi.editor.ReadOnlyModificationException
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.vfs.VirtualFile
import org.intellij.images.ide.EditorImagePasteProvider
import org.intellij.plugins.markdown.lang.MarkdownFileType

internal class MarkdownImagePasteProvider : EditorImagePasteProvider() {
  override val supportedFileType: FileType
    get() = MarkdownFileType.INSTANCE

  override fun Editor.imageFilePasted(imageFile: VirtualFile) {
    val pastedFileName = imageFile.name

    caretModel.currentCaret.offset.let { currentCaretOffset ->
      val textToInsert = ImageUtils.createMarkdownImageText(pastedFileName, pastedFileName)
      try {
        document.insertString(currentCaretOffset, textToInsert)
        caretModel.moveToOffset(currentCaretOffset + textToInsert.length)
      }
      catch (e: ReadOnlyModificationException) {
        // do nothing
      }
      catch (e: ReadOnlyFragmentModificationException) {
        // do nothing
      }
    }
  }
}
