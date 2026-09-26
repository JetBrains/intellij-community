// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal

import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object TerminalEditorTabSupportUtil {
  private const val REGISTRY_KEY = "toolwindow.open.tab.in.editor"

  fun isNewImplementationEnabled(): Boolean = Registry.`is`(REGISTRY_KEY, false)

  fun isOldImplementationEnabled(): Boolean = !isNewImplementationEnabled()
}
