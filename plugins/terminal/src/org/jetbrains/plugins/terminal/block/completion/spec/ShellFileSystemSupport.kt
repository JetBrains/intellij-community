// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec

import com.intellij.terminal.completion.spec.ShellFileInfo
import org.jetbrains.annotations.ApiStatus

/**
 * This interface is used to provide file system support for file-system-related methods
 * of [com.intellij.terminal.completion.spec.ShellRuntimeContext].
 * For example, [com.intellij.terminal.completion.spec.ShellRuntimeContext.listDirectoryFiles].
 */
@ApiStatus.Experimental
interface ShellFileSystemSupport {
  /**
   * @param path absolute os-dependent path to the directory.
   */
  suspend fun listDirectoryFiles(path: String): List<ShellFileInfo>
}