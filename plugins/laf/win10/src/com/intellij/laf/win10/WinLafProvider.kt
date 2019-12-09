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
    return Win10LookAndFeelInfo()
  }

  private class Win10LookAndFeelInfo : PluggableLafInfo(LAF_NAME, WinIntelliJLaf::class.java.name) {
    override fun createLookAndFeel(): LookAndFeel {
      val laf = WinIntelliJLaf()
      laf.putUserData(UIUtil.PLUGGABLE_LAF_KEY, name)
      return laf
    }

    override fun createSearchAreaPainter(context: SearchAreaContext): SearchTextAreaPainter {
      return Win10SearchPainter(context)
    }

    override fun createEditorTextFieldBorder(editorTextField: EditorTextField, editor: EditorEx): DarculaEditorTextFieldBorder {
      return WinIntelliJEditorTextFieldBorder(editorTextField, editor)
    }

    override fun updateDefaults(defaults: UIDefaults) {
      defaults["Menu.arrowIcon"] = Win10MenuArrowIcon()
    }
  }

  private class Win10MenuArrowIcon :
    MenuArrowIcon(WinIconLookup.getIcon(name = MENU_TRIANGLE_ICON_NAME),
                  WinIconLookup.getIcon(name = MENU_TRIANGLE_ICON_NAME, selected = true),
                  WinIconLookup.getIcon(name = MENU_TRIANGLE_ICON_NAME, enabled = false))

  companion object {
    const val LAF_NAME = "Windows 10 Light"
    const val MENU_TRIANGLE_ICON_NAME = "menuTriangle"
  }
}