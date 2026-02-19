package com.intellij.python.hatch.impl

import com.intellij.python.hatch.icons.PythonHatchIcons
import com.intellij.python.processOutput.ProcessBinaryFileName
import com.intellij.python.processOutput.ProcessIcon
import com.intellij.python.processOutput.ProcessOutputIconMapping

internal class HatchIconMapping : ProcessOutputIconMapping() {
  override val mapping: Map<ProcessBinaryFileName, ProcessIcon> =
    mapOf(
      ProcessBinaryFileName("hatch") to ProcessIcon(PythonHatchIcons.Logo, PythonHatchIcons::class.java)
    )
}