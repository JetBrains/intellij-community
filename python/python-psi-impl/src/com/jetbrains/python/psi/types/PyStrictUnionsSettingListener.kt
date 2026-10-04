// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types

import com.intellij.openapi.options.advanced.AdvancedSettingsChangeListener

/**
 * Runs the analysis again in each open project when the user changes [PyUnionType.STRICT_UNIONS_SETTING].
 *
 * @see restartTypeAnalysis
 */
internal class PyStrictUnionsSettingListener : AdvancedSettingsChangeListener {
  override fun advancedSettingChanged(id: String, oldValue: Any, newValue: Any) {
    if (id != PyUnionType.STRICT_UNIONS_SETTING) return
    restartTypeAnalysis("PyStrictUnionsSettingListener: ${PyUnionType.STRICT_UNIONS_SETTING} changed")
  }
}
