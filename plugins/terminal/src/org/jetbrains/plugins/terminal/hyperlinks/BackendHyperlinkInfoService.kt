// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.hyperlinks

import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.openapi.components.service
import com.intellij.terminal.session.TerminalHyperlinkId
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.reworked.session.rpc.TerminalSessionId
import java.awt.event.MouseEvent

/**
 * A backend-only service to access information about hyperlinks.
 *
 * Located in the shared module because it's needed for context menu action groups
 * that contain a mix of frontend and backend actions.
 */
@ApiStatus.Internal
interface BackendHyperlinkInfoService {
  companion object {
    @JvmStatic fun getInstance(): BackendHyperlinkInfoService = service()
  }
  fun getHyperlinkInfo(sessionId: TerminalSessionId, isAlternateBuffer: Boolean, hyperlinkId: TerminalHyperlinkId): BackendHyperlinkInfo?
}

/**
 * Information about a backend hyperlink.
 */
@ApiStatus.Internal
data class BackendHyperlinkInfo(
  /** The info instance returned by the console filter. */
  val hyperlinkInfo: HyperlinkInfo,
  /**
   *  A fake mouse event to use to call [com.intellij.execution.filters.HyperlinkWithPopupMenuInfo.getPopupMenuGroup].
   *  */
  val fakeMouseEvent: MouseEvent,
)