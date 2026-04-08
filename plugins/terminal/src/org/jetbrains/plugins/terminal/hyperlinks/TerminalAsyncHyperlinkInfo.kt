// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.hyperlinks

import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.execution.filters.navigate
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.project.Project
import com.intellij.util.SlowOperations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface TerminalAsyncHyperlinkInfo : HyperlinkInfo {
  suspend fun navigate(project: Project, mouseEvent: EditorMouseEvent?)
}

@ApiStatus.Internal
object TerminalHyperlinkNavigator {
  private val crossProjectNavigator = TerminalCrossProjectFileHyperlinkNavigator()

  suspend fun navigate(project: Project, hyperlinkInfo: HyperlinkInfo, mouseEvent: EditorMouseEvent?) {
    if (crossProjectNavigator.navigate(project, hyperlinkInfo, mouseEvent)) {
      return
    }
    if (hyperlinkInfo is TerminalAsyncHyperlinkInfo) {
      hyperlinkInfo.navigate(project, mouseEvent)
    }
    else {
      navigateDefault(project, hyperlinkInfo, mouseEvent)
    }
  }

  suspend fun navigateDefault(project: Project, hyperlinkInfo: HyperlinkInfo, mouseEvent: EditorMouseEvent?) {
    withContext(Dispatchers.EDT) { // navigation might need the WIL
      SlowOperations.startSection(SlowOperations.ACTION_PERFORM).use {
        hyperlinkInfo.navigate(project, mouseEvent?.editor, mouseEvent?.logicalPosition)
      }
    }
  }
}
