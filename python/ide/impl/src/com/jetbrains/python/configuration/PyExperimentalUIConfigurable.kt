// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.configuration

import com.intellij.ui.ExperimentalUIConfigurable
import com.intellij.util.PlatformUtils

class PyExperimentalUIConfigurable : ExperimentalUIConfigurable() {

  override fun getExploreNewUiUrl(): String {
    return EXPLORE_NEW_UI_URL_TEMPLATE.format("pycharm")
  }
}
