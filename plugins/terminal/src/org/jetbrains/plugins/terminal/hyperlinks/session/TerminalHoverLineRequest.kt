// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.hyperlinks.session

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

/**
 * A request to find invisible hyperlinks in the output line under the mouse pointer.
 */
@ApiStatus.Internal
@Serializable
data class TerminalHoverLineRequest(
  /**
   * The hovered output line without its line break.
   */
  val text: String,
  /**
   * The absolute offset of the first character of [text] in the terminal output.
   */
  val startOffset: Long,
  /**
   * Identifies this request, so that later requests can list it in [retainedIds].
   */
  val id: Long,
  /**
   * The ids of the earlier requests whose hyperlinks the frontend still shows.
   *
   * The backend forgets the hyperlinks of all other earlier requests,
   * so they can no longer be followed.
   */
  val retainedIds: List<Long>,
)
