// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.implTraining

import com.intellij.openapi.wm.impl.DialogBackgroundImageProviderBase
import java.net.URL

internal class PyCharmDialogBackgroundImageProvider : DialogBackgroundImageProviderBase() {
  override fun getImageUrl(isDark: Boolean, isIslands: Boolean): URL? {
    return javaClass.getResource(if (isDark && isIslands) "/img/pycharm-onboarding-gradient-background-islands-dark.svg" else "/img/pycharm-onboarding-gradient-background.svg")
  }
}