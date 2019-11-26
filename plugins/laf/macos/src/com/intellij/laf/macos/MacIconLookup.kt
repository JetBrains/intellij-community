package com.intellij.laf.macos

import com.intellij.icons.AllIcons
import com.intellij.util.ui.LafIconLookup
import com.intellij.util.ui.UIUtil
import javax.swing.Icon

object MacIconLookup {
  private const val ICONS_DIR_PREFIX = "/icons/"

  @JvmStatic
  @JvmOverloads
  fun getIcon(name: String,
              selected: Boolean = false,
              focused: Boolean = false,
              enabled: Boolean = true,
              editable: Boolean = false,
              pressed: Boolean = false): Icon {

    return LafIconLookup.findIcon( name,
      selected = selected,
      focused = focused,
      enabled = enabled,
      editable = editable,
      pressed = pressed,
      isThrowErrorIfNotFound = true,
      dirProvider = { ICONS_DIR_PREFIX + if (UIUtil.isGraphite()) "graphite/" else "blue/" } )
           ?: AllIcons.Actions.Stub
  }
}