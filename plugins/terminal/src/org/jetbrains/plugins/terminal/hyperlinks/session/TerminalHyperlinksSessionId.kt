// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.hyperlinks.session

import com.intellij.openapi.actionSystem.DataKey
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@Serializable
@ApiStatus.Internal
data class TerminalHyperlinksSessionId(val id: Int) {
  companion object {
    @JvmStatic
    val DATA_KEY: DataKey<TerminalHyperlinksSessionId> = DataKey.create("TerminalHyperlinksSessionId")
  }
}