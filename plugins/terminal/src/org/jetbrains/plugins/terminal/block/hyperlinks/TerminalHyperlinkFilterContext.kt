// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.hyperlinks

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.eel.EelDescriptor
import org.jetbrains.annotations.ApiStatus

/**
 * Provides information about the current shell.
 */
@ApiStatus.Internal
interface TerminalHyperlinkFilterContext {
  /**
   * The environment where the shell is running (local/WSL/Docker/SSH).
   */
  val eelDescriptor: EelDescriptor

  /**
   * The current working directory of the running shell.
   * This value is updated when the directory is changed by user (e.g., via `cd`).
   * If not `null`, the value is guaranteed to be a valid directory.
   * 
   * Returns `null` if the working directory cannot be determined. It happens when both conditions are met:
   * 1. Shell integration is disabled in Settings or is unavailable for the current shell (e.g., Command Prompt, Git Bash).
   * 2. No OS-specific process API is available to retrieve the working directory of the current shell (e.g., PowerShell, Git Bash).
   */
  val currentWorkingDirectory: VirtualFile?
}
