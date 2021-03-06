// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.ui

import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.util.IconUtil
import com.intellij.util.ui.JBUI
import java.awt.Component
import javax.swing.Icon

fun requestFocus(component: Component?) {
  if (component != null) {
    IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown {
      IdeFocusManager.getGlobalInstance().requestFocus(component, true)
    }
  }
}

fun resizeIcon(icon: Icon, size: Int): Icon {
  val scale = JBUI.scale(size).toFloat() / icon.iconWidth.toFloat()
  return IconUtil.scale(icon, null, scale)
}

@NlsSafe
fun cleanupUrl(@NlsSafe url: String): String = url
  .removePrefix("https://")
  .removePrefix("http://")
  .removeSuffix("/")
