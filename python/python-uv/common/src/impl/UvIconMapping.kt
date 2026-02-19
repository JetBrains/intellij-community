// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.uv.common.impl

import com.intellij.python.community.impl.uv.common.icons.PythonCommunityImplUVCommonIcons
import com.intellij.python.processOutput.ProcessBinaryFileName
import com.intellij.python.processOutput.ProcessIcon
import com.intellij.python.processOutput.ProcessOutputIconMapping

internal class UvIconMapping : ProcessOutputIconMapping() {
  override val mapping: Map<ProcessBinaryFileName, ProcessIcon> =
    mapOf(
      ProcessBinaryFileName("uv") to
        ProcessIcon(PythonCommunityImplUVCommonIcons.UV, PythonCommunityImplUVCommonIcons::class.java)
    )
}