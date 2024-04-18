// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp.prompt.lang

import com.intellij.openapi.fileTypes.LanguageFileType
import org.jetbrains.plugins.terminal.TerminalBundle
import javax.swing.Icon

internal object TerminalPromptFileType : LanguageFileType(TerminalPromptLanguage) {
  override fun getName(): String = "Terminal Prompt"

  override fun getDescription(): String = TerminalBundle.message("terminal.prompt.lang.description")

  override fun getDefaultExtension(): String = "prompt"

  override fun getIcon(): Icon? = null

  // Do not show this file type in the File Types configurable
  override fun isReadOnly(): Boolean = true
}