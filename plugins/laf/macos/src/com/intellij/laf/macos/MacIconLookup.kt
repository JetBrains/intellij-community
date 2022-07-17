package com.intellij.laf.macos

import com.intellij.icons.AllIcons
import com.intellij.util.ui.DirProvider
import com.intellij.util.ui.LafIconLookup
import com.intellij.util.ui.UIUtil
import javax.swing.Icon

private object MacDirProvider : DirProvider() {
  override val defaultExtension: String
    get() = "svg"

  override fun dir() = "icons/" + if (UIUtil.isGraphite()) "graphite/" else "blue/"
}

object MacIconLookup {
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
                                  dirProvider = MacDirProvider) ?: AllIcons.Actions.Stub
  }
}