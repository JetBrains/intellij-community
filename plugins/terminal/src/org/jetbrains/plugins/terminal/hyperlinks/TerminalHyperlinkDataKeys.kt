// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.hyperlinks

import com.intellij.openapi.actionSystem.DataKey
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.hyperlinks.session.TerminalHyperlinksSessionId

/**
 * The data keys are not in the companions of the ID classes, so that creating a data snapshot
 * does not initialize the serializable ID classes.
 */
@ApiStatus.Internal
object TerminalHyperlinkDataKeys {
  @JvmField
  val HYPERLINK_ID: DataKey<TerminalHyperlinkId> = DataKey.create("TerminalHyperlinkId")

  @JvmField
  val SESSION_ID: DataKey<TerminalHyperlinksSessionId> = DataKey.create("TerminalHyperlinksSessionId")
}
