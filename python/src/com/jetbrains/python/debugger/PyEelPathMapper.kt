// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger

import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.path.EelPathException
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.equalsMayBeUsingMachine
import com.jetbrains.python.remote.PyRemotePathMapper
import java.nio.file.InvalidPathException
import java.nio.file.Path

/**
 * Maps paths between the IDE and [eel], for example `\\wsl.localhost\Ubuntu\home\user` and `/home/user`.
 * The debugger uses it to convert the breakpoint and the frame positions.
 */
internal class PyEelPathMapper(private val eel: EelDescriptor) : PyRemotePathMapper() {
  override fun convertToRemote(localPath: String): String = pathOnEelOrNull(localPath) ?: localPath

  override fun convertToLocal(remotePath: String): String =
    try {
      EelPath.parse(remotePath, eel).asNioPath().toString()
    }
    catch (_: EelPathException) {
      remotePath
    }

  override fun isEmpty(): Boolean = false

  /**
   * Names [localPath] the way a process on [eel] sees it. Returns `null` for a relative path and for a path on another eel.
   */
  private fun pathOnEelOrNull(localPath: String): String? {
    val path = try {
      Path.of(localPath)
    }
    catch (_: InvalidPathException) {
      // The debugger asks for any position, and not every position is a file path
      return null
    }
    return path.takeIf { it.isAbsolute }?.asEelPath()?.takeIf { it.descriptor.equalsMayBeUsingMachine(eel) }?.toString()
  }
}
