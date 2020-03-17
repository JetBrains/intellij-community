package com.intellij.laf.win10

import com.intellij.icons.AllIcons
import com.intellij.util.ui.DirProvider
import com.intellij.util.ui.LafIconLookup
import javax.swing.Icon

private class WinDirProvider : DirProvider() {
  override fun dir(): String = "/icons/"
}

object WinIconLookup {
  @JvmStatic
  @JvmOverloads
  fun getIcon(name: String,
              selected: Boolean = false,
              focused: Boolean = false,
              enabled: Boolean = true,
              editable: Boolean = false,
              pressed: Boolean = false): Icon {

    return LafIconLookup.findIcon(name,
                                  selected = selected,
                                  focused = focused,
                                  enabled = enabled,
                                  editable = editable,
                                  pressed = pressed,
                                  isThrowErrorIfNotFound = true,
                                  dirProvider = WinDirProvider()) ?: AllIcons.Actions.Stub
  }
}