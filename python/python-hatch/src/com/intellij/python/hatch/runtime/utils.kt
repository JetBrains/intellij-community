package com.intellij.python.hatch.runtime

import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelPlatform

fun EelApi.getHatchCommand(): String = when (platform) {
  is EelPlatform.Windows -> "hatch.exe"
  else -> "hatch"
}
