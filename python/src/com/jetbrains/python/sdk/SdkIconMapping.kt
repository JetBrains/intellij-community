// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.icons.AllIcons
import com.intellij.python.processOutput.common.ProcessIcon
import com.intellij.python.processOutput.common.ProcessMatcher
import com.intellij.python.processOutput.common.ProcessOutputIconMapping

internal class SdkIconMapping : ProcessOutputIconMapping() {
  override val matchers: List<ProcessMatcher> = listOf(
    ProcessMatcher(
      { it.name.startsWith("python") },
      ProcessIcon(AllIcons.Language.Python, AllIcons.Language::class.java)
    )
  )
}
