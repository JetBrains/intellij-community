// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.prompt.lang

import com.intellij.openapi.fileTypes.LanguageFileType
import org.jetbrains.plugins.terminal.TerminalBundle
import javax.swing.Icon

/**
 * Represents an internal file type for the prompt in the new terminal.
 *
 * It is not registered in plugin.xml so that it cannot be used accidentally for `*.prompt` files.
 * This also helps to hide it in "Settings | Editor | File Types".
 * Note that as another result, [com.intellij.openapi.fileTypes.FileTypeManager] doesn't know about
 * this file type, for example,
 * - `FileTypeRegistry.getInstance().registeredFileTypes` won't list it;
 * - `TerminalPromptLanguage.associatedFileType` will return null.
 */
internal object TerminalPromptFileType : LanguageFileType(TerminalPromptLanguage) {
  override fun getName(): String = "Terminal Prompt"

  override fun getDescription(): String = TerminalBundle.message("terminal.prompt.lang.description")

  override fun getDefaultExtension(): String = "prompt"

  override fun getIcon(): Icon? = null
}
