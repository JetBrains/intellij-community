// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review

import com.intellij.util.ui.JBValue

internal object ReviewUiSpec {
  val avatarSizeIntValue: JBValue = JBValue.UIInteger("space.avatar.size", 18)


  object ReviewersSelector {
    const val VISIBLE_ROWS_COUNT: Int = 7
  }
}
