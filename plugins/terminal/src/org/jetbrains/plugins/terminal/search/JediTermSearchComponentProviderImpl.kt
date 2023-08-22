// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.search

import com.intellij.terminal.JBTerminalWidget
import com.intellij.terminal.search.JediTermSearchComponentProvider
import com.jediterm.terminal.ui.JediTermSearchComponent

class JediTermSearchComponentProviderImpl : JediTermSearchComponentProvider {
  override fun createSearchComponent(jediTermWidget: JBTerminalWidget): JediTermSearchComponent {
    return TerminalSearchSession(jediTermWidget).getTerminalSearchComponent()
  }
}
