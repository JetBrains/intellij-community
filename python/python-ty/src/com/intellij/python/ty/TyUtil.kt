package com.intellij.python.ty

import com.intellij.python.ty.common.icons.PythonTyCommonIcons
import com.intellij.util.IconUtil
import javax.swing.Icon

object TyUtil {
  fun getDefaultTyIcon(): Icon {
    return IconUtil.downscaleIconToSize(PythonTyCommonIcons.TY, 16, 16)
  }
}
