package com.intellij.searchEverywhereMl.typos

import com.intellij.openapi.options.advanced.AdvancedSettings

internal val isTypoFixingEnabled: Boolean
  get() = AdvancedSettings.getBoolean("searcheverywhere.ml.typos.enable")

