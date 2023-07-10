// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.sh

import com.intellij.lang.Language
import com.intellij.sh.ShLanguage
import org.jetbrains.plugins.terminal.exp.completion.TerminalShellSupport

abstract class BaseShSupport : TerminalShellSupport {
  override val promptLanguage: Language
    get() = ShLanguage.INSTANCE
}