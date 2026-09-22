// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyrefly

import com.intellij.execution.ExecutionException
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.io.NioFiles
import com.intellij.platform.eel.isWindows
import com.intellij.platform.eel.provider.localEel
import com.intellij.util.system.CpuArch
import com.intellij.util.system.OS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.isRegularFile

private val LOG = fileLogger()

private const val PYREFLY_DIR_NAME: String = "pyrefly"

private const val PYREFLY_BINARY_NAME: String = "pyrefly"

/** `python-ce` carries the executable when a product bundles it. */
private val PYTHON_CORE_PLUGIN_ID: PluginId = PluginId.getId("PythonCore")

/** One artifact for each platform. A published Python plugin declares it, so both plugins are installed together. */
private val PYREFLY_BINARIES_PLUGIN_ID: PluginId = PluginId.getId("com.intellij.python.pyrefly.binaries")

internal object PyreflyExecutableProvider {
  /**
   * The Pyrefly executable of this host.
   *
   * @throws ExecutionException when no installed plugin carries an executable for this host
   */
  suspend fun getExecutable(): Path {
    val relativePath = hostRelativePath()
                       ?: throw ExecutionException(PyreflyBundle.message("pyrefly.executable.unsupported.host"))
    for (pluginId in listOf(PYTHON_CORE_PLUGIN_ID, PYREFLY_BINARIES_PLUGIN_ID)) {
      executableIn(PluginManagerCore.getPlugin(pluginId), relativePath)?.let { return it }
    }
    throw ExecutionException(PyreflyBundle.message("pyrefly.executable.unavailable", relativePath.toString()))
  }

  /** The executable inside [plugin], or `null` when the plugin is absent or carries no executable for this host. */
  private suspend fun executableIn(plugin: IdeaPluginDescriptor?, relativePath: Path): Path? {
    val executable = plugin?.pluginPath?.resolve(relativePath) ?: return null
    return withContext(Dispatchers.IO) {
      if (!executable.isRegularFile()) {
        return@withContext null
      }
      try {
        // A plugin zip can arrive without the executable bit.
        NioFiles.setExecutable(executable)
      }
      catch (e: IOException) {
        LOG.warn("Cannot set the executable bit on the Pyrefly executable $executable", e)
        return@withContext null
      }
      executable
    }
  }

  /** The directory name has to stay the same as `pyreflyPlatformDirName` in `pyreflyBundling.kt`. */
  private fun hostRelativePath(): Path? {
    val os = when (OS.CURRENT) {
      OS.Windows -> "Windows"
      OS.macOS -> "macOS"
      OS.Linux -> "Linux"
      else -> return null
    }
    val arch = when (CpuArch.CURRENT) {
      CpuArch.X86_64 -> "X86_64"
      CpuArch.ARM64 -> "AArch64"
      else -> return null
    }
    val binaryName = if (localEel.platform.isWindows) "$PYREFLY_BINARY_NAME.exe" else PYREFLY_BINARY_NAME
    return Path.of(PYREFLY_DIR_NAME, "$os-$arch", binaryName)
  }
}
