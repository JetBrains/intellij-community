package com.intellij.python.pyrefly

import com.intellij.python.pyrefly.common.icons.PythonPyreflyCommonIcons
import com.intellij.util.IconUtil
import javax.swing.Icon

object PyreflyUtil {
  fun getDefaultPyreflyIcon(): Icon {
    return IconUtil.resizeSquared(PythonPyreflyCommonIcons.Pyrefly, 16)
  }
}