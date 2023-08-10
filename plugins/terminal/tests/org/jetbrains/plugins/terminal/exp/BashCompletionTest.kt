// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class BashCompletionTest : BaseShCompletionTest() {
  override val shellPath: String = "/bin/bash"

  override fun createCompletionManager(session: TerminalSession): TerminalCompletionManager {
    return BashCompletionManager(session)
  }
}