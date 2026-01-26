// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal

import org.jetbrains.annotations.ApiStatus
import java.util.EventListener

@ApiStatus.Experimental
fun interface TerminalCommandStartedListener : EventListener {
  fun commandStarted(command: String)
}