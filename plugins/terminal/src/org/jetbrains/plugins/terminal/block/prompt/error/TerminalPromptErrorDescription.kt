// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.prompt.error

import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

@ApiStatus.Internal
interface TerminalPromptErrorDescription {
  val errorText: String

  val icon: Icon?
    get() = null

  val linkText: String?
    get() = null

  fun onLinkClick() = Unit
}