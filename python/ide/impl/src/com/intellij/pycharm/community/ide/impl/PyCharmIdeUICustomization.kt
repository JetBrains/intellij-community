// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl

import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.IdeUICustomization

internal class PyCharmIdeUICustomization : IdeUICustomization() {
  override fun getPaidVersionName(): @NlsSafe String = "Pro"
}
