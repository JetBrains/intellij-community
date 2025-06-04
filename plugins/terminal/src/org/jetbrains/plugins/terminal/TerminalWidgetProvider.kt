// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal

import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.terminal.ui.TerminalWidget
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.fus.TerminalStartupFusInfo

/**
 * It is a hack to get the TerminalWidget implementation from the Frontend.
 * It should be used in the shared code that can't be fully extracted to the Frontend because of external usages.
 */
@ApiStatus.Internal
interface TerminalWidgetProvider {
  fun createTerminalWidget(project: Project, startupFusInfo: TerminalStartupFusInfo?, parentDisposable: Disposable): TerminalWidget

  companion object {
    private val EP_NAME = ExtensionPointName<TerminalWidgetProvider>("org.jetbrains.plugins.terminal.terminalWidgetProvider")

    @JvmStatic
    fun getProvider(): TerminalWidgetProvider? {
      return EP_NAME.extensionList.firstOrNull()
    }
  }
}