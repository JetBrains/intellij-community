// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked.lang

import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.NonNls
import javax.swing.Icon

internal object TerminalOutputFileType : LanguageFileType(TerminalOutputLanguage) {
  override fun getName(): @NonNls String = "TerminalOutput"

  override fun getDescription(): @NlsContexts.Label String = ""

  override fun getDefaultExtension(): @NlsSafe String = ""

  override fun getIcon(): Icon? = null

  override fun isReadOnly(): Boolean = true
}