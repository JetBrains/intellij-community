// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.impl

import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.fs.EelFileInfo
import com.intellij.platform.eel.fs.listDirectoryWithAttrs
import com.intellij.platform.eel.getOrNull
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.path.EelPathException
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.terminal.completion.spec.ShellFileInfo
import kotlinx.coroutines.CancellationException
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.completion.spec.ShellFileSystemSupport

@ApiStatus.Internal
class ShellFileSystemSupportImpl(private val eelDescriptor: EelDescriptor) : ShellFileSystemSupport {
  override suspend fun listDirectoryFiles(path: String): List<ShellFileInfo> {
    val eelPath = try {
      EelPath.parse(path, eelDescriptor)
    }
    catch (_: EelPathException) {
      return emptyList()
    }

    return try {
      val eelApi = eelDescriptor.toEelApi()
      val result = eelApi.fs.listDirectoryWithAttrs(eelPath).doNotResolve().eelIt()
      val files = result.getOrNull() ?: emptyList()
      files.map { (name, info) ->
        ShellFileInfo.create(name, info.type.toShellFileType())
      }
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Exception) {
      LOG.error("Failed to get child files for path: $path", e)
      emptyList()
    }
  }

  private fun EelFileInfo.Type.toShellFileType(): ShellFileInfo.Type {
    return when (this) {
      is EelFileInfo.Type.Regular -> ShellFileInfo.Type.FILE
      is EelFileInfo.Type.Directory -> ShellFileInfo.Type.DIRECTORY
      else -> ShellFileInfo.Type.OTHER
    }
  }

  companion object {
    private val LOG = logger<ShellFileSystemSupportImpl>()
  }
}