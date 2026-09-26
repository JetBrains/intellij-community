package com.intellij.python.pyright

import com.intellij.python.pyright.common.icons.PythonPyrightCommonIcons
import com.intellij.util.IconUtil
import javax.swing.Icon

object PyrightUtil {
  fun getDefaultPyrightIcon(): Icon {
    return IconUtil.resizeSquared(PythonPyrightCommonIcons.Pyright, 16)
  }
  fun getDefaultBasedPyrightIcon(): Icon {
    return IconUtil.resizeSquared(PythonPyrightCommonIcons.Basedpyright, 16)
  }
}