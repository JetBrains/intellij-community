// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.chat.ui

import javax.swing.JPanel

internal abstract class HoverableJPanel : JPanel(null) {
  abstract fun hoverStateChanged(isHovered: Boolean)
}