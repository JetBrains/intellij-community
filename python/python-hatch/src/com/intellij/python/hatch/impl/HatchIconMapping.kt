package com.intellij.python.hatch.impl

import com.intellij.python.hatch.common.icons.PythonHatchCommonIcons
import com.intellij.python.processOutput.common.ProcessBinaryFileName
import com.intellij.python.processOutput.common.ProcessIcon
import com.intellij.python.processOutput.common.ProcessOutputIconMapping

internal class HatchIconMapping : ProcessOutputIconMapping() {
  override val mapping: Map<ProcessBinaryFileName, ProcessIcon> =
    mapOf(
      ProcessBinaryFileName("hatch") to ProcessIcon(PythonHatchCommonIcons.Logo, PythonHatchCommonIcons::class.java)
    )
}