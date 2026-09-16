package com.intellij.python.ruff

import com.intellij.python.ruff.common.icons.PythonRuffCommonIcons
import com.intellij.util.IconUtil
import javax.swing.Icon

object RuffUtil {
  const val RUFF_WEBSITE: String = "https://docs.astral.sh/ruff"

  fun getDefaultRuffIcon(): Icon {
    return IconUtil.resizeSquared(PythonRuffCommonIcons.Ruff, 16)
  }
}
