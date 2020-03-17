package com.intellij.laf.win10

import com.intellij.ide.ui.LafProvider
import com.intellij.ide.ui.laf.MenuArrowIcon
import com.intellij.ide.ui.laf.PluggableLafInfo
import com.intellij.ide.ui.laf.SearchTextAreaPainter
import com.intellij.ide.ui.laf.darcula.ui.DarculaEditorTextFieldBorder
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.ui.EditorTextField
import com.intellij.util.ui.UIUtil
import javax.swing.LookAndFeel
import javax.swing.UIDefaults

class WinLafProvider : LafProvider {
  override fun getLookAndFeelInfo(): PluggableLafInfo {
    return infoInstance
  }

  private class Win10LookAndFeelInfo : PluggableLafInfo(LAF_NAME, WinIntelliJLaf::class.java.name) {
    override fun createLookAndFeel(): LookAndFeel = WinIntelliJLaf()

    override fun createSearchAreaPainter(context: SearchAreaContext): SearchTextAreaPainter {
      return Win10SearchPainter(context)
    }

    override fun createEditorTextFieldBorder(editorTextField: EditorTextField, editor: EditorEx): DarculaEditorTextFieldBorder {
      return WinIntelliJEditorTextFieldBorder(editorTextField, editor)
    }
  }

  companion object {
    const val LAF_NAME = "Windows 10 Light"
    val infoInstance:PluggableLafInfo = Win10LookAndFeelInfo()
  }
}