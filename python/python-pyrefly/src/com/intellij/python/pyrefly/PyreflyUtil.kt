package com.intellij.python.pyrefly

import com.intellij.python.pyrefly.icons.PythonPyreflyIcons
import com.intellij.util.IconUtil
import javax.swing.Icon

object PyreflyUtil {
  fun getDefaultPyreflyIcon(): Icon {
    return IconUtil.resizeSquared(PythonPyreflyIcons.Pyrefly, 16)
  }
}