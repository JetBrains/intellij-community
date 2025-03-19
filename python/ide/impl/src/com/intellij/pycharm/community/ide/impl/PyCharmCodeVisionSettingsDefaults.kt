// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl

import com.intellij.codeInsight.codeVision.CodeVisionAnchorKind
import com.intellij.codeInsight.codeVision.settings.CodeVisionSettingsDefaults

class PyCharmCodeVisionSettingsDefaults : CodeVisionSettingsDefaults {
  override val defaultPosition: CodeVisionAnchorKind get() = CodeVisionAnchorKind.Right
}