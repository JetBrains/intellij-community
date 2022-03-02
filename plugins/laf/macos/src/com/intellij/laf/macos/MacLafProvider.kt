package com.intellij.laf.macos

import com.intellij.ide.ui.LafProvider
import com.intellij.ide.ui.laf.PluggableLafInfo
import com.intellij.ide.ui.laf.darcula.ui.DarculaEditorTextFieldBorder
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.EditorTextField
import javax.swing.LookAndFeel

internal class MacLafProvider : LafProvider {
  override fun getLookAndFeelInfo() = instance

  private class MacOsLookAndFeelInfo : PluggableLafInfo(LAF_NAME, MacIntelliJLaf::class.java.name) {
    override fun createLookAndFeel(): LookAndFeel = MacIntelliJLaf()

    override fun createSearchAreaPainter(context: SearchAreaContext) = MacSearchPainter(context)

    override fun createEditorTextFieldBorder(editorTextField: EditorTextField, editor: EditorEx): DarculaEditorTextFieldBorder {
      return MacEditorTextFieldBorder(editorTextField, editor)
    }
  }

  companion object {
    @NlsSafe
    const val LAF_NAME = "macOS Light"

    private val instance: PluggableLafInfo = MacOsLookAndFeelInfo()
  }
}