package com.intellij.python.pyright

import com.intellij.python.pyright.icons.PythonPyrightIcons
import com.intellij.util.IconUtil
import javax.swing.Icon

object PyrightUtil {
  fun getDefaultPyrightIcon(): Icon {
    return IconUtil.resizeSquared(PythonPyrightIcons.Pyright, 16)
  }
  fun getDefaultBasedPyrightIcon(): Icon {
    return IconUtil.resizeSquared(PythonPyrightIcons.Basedpyright, 16)
  }
}