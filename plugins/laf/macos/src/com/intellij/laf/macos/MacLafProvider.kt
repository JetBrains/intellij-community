package com.intellij.laf.macos

import com.intellij.ide.ui.LafProvider
import com.intellij.ide.ui.laf.PluggableLafInfo
import com.intellij.ide.ui.laf.SearchTextAreaPainter
import com.intellij.ide.ui.laf.darcula.ui.DarculaEditorTextFieldBorder
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.ui.EditorTextField
import com.intellij.util.ui.UIUtil
import javax.swing.LookAndFeel
import javax.swing.UIDefaults

class MacLafProvider : LafProvider {
  override fun getLookAndFeelInfo(): PluggableLafInfo {
    return infoInstance
  }

  private class MacOsLookAndFeelInfo : PluggableLafInfo(LAF_NAME, MacIntelliJLaf::class.java.name) {
    override fun createLookAndFeel(): LookAndFeel = MacIntelliJLaf()

    override fun createSearchAreaPainter(context: SearchAreaContext): SearchTextAreaPainter {
      return MacSearchPainter(context)
    }

    override fun createEditorTextFieldBorder(editorTextField: EditorTextField, editor: EditorEx): DarculaEditorTextFieldBorder {
      return MacEditorTextFieldBorder(editorTextField, editor)
    }
  }

  companion object {
    const val LAF_NAME = "macOS Light"
    val infoInstance:PluggableLafInfo = MacOsLookAndFeelInfo()
  }
}