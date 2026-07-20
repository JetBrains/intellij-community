// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal

import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface TerminalEditorTabInfo {
  @NlsSafe
  fun getEditorTabTitle(): String

  suspend fun shouldConfirmClosing(): Boolean
}

@ApiStatus.Internal
object TerminalEditorTabSupportUtil {
  private const val REGISTRY_KEY = "toolwindow.open.tab.in.editor"

  @JvmField
  val TERMINAL_EDITOR_TAB_INFO_KEY: Key<TerminalEditorTabInfo> = Key.create("Terminal.EditorTabInfo")

  fun isNewImplementationEnabled(): Boolean = Registry.`is`(REGISTRY_KEY, false)

  fun isOldImplementationEnabled(): Boolean = !isNewImplementationEnabled()
}
