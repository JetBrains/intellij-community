// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.conda

import com.intellij.python.community.impl.conda.common.icons.PythonCommunityImplCondaCommonIcons
import com.intellij.python.processOutput.common.ProcessBinaryFileName
import com.intellij.python.processOutput.common.ProcessIcon
import com.intellij.python.processOutput.common.ProcessOutputIconMapping

internal class CondaIconMapping : ProcessOutputIconMapping() {
  override val mapping: Map<ProcessBinaryFileName, ProcessIcon> = mapOf(
    ProcessBinaryFileName("conda") to
      ProcessIcon(PythonCommunityImplCondaCommonIcons.Anaconda, PythonCommunityImplCondaCommonIcons::class.java),
  )
}
