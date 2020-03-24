package com.intellij.space.vcs.review

import com.intellij.util.ui.JBValue

internal object ReviewUiSpec {
  val avatarSizeIntValue: JBValue = JBValue.UIInteger("space.avatar.size", 18)


  object ReviewersSelector {
    const val VISIBLE_ROWS_COUNT: Int = 7
  }
}
