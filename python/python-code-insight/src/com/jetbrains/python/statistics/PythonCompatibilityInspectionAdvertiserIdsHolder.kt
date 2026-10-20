// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.statistics

import com.intellij.notification.impl.NotificationIdsHolder

internal class PythonCompatibilityInspectionAdvertiserIdsHolder : NotificationIdsHolder {
  override fun getNotificationIds(): List<String> = listOf(
    STALE_PYTHON_VERSION,
    USING_FUTURE_IMPORTS,
    USING_SIX_PACKAGE,
  )

  companion object {
    const val STALE_PYTHON_VERSION = "stale.python.version"
    const val USING_FUTURE_IMPORTS = "using.future.imports"
    const val USING_SIX_PACKAGE = "using.six.package"
  }
}
