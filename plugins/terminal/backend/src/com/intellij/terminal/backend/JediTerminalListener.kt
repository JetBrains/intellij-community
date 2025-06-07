// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.backend

import java.util.EventListener

internal interface JediTerminalListener : EventListener {
  fun arrowKeysModeChanged(isApplication: Boolean) {}

  fun keypadModeChanged(isApplication: Boolean) {}

  fun autoNewLineChanged(isEnabled: Boolean) {}

  fun altSendsEscapeChanged(isEnabled: Boolean) {}

  fun beforeAlternateScreenBufferChanged(isEnabled: Boolean) {}
}