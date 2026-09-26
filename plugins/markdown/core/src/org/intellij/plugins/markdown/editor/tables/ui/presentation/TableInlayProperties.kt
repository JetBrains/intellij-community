// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables.ui.presentation

import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil

internal object TableInlayProperties {
  const val barSize = 6
  const val topDownPadding = 2
  const val leftRightPadding = 2

  val barColor
    get() = UIUtil.toAlpha(JBColor.DARK_GRAY, 50)

  val barHoverColor
    get() = UIUtil.toAlpha(JBColor.BLUE, 50)

  val circleColor
    get() = barColor

  val circleHoverColor
    get() = barHoverColor
}
