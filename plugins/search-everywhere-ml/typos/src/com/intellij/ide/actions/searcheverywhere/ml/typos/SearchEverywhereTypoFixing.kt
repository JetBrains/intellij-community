package com.intellij.ide.actions.searcheverywhere.ml.typos

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.advanced.AdvancedSettings


internal val isTypoFixingEnabled: Boolean
  get() = ApplicationManager.getApplication().isInternal
          && AdvancedSettings.getBoolean("searcheverywhere.ml.typos.enable")
