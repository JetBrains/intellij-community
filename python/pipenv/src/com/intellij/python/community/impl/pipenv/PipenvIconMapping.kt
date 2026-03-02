// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.pipenv

import com.intellij.python.community.impl.pipenv.icons.PythonCommunityImplPipenvIcons
import com.intellij.python.processOutput.common.ProcessBinaryFileName
import com.intellij.python.processOutput.common.ProcessIcon
import com.intellij.python.processOutput.common.ProcessOutputIconMapping

internal class PipenvIconMapping : ProcessOutputIconMapping() {
  override val mapping: Map<ProcessBinaryFileName, ProcessIcon> = mapOf(
    ProcessBinaryFileName("pipenv") to ProcessIcon(PIPENV_ICON, PythonCommunityImplPipenvIcons::class.java)
  )
}