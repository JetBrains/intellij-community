// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.startup

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.LocalEelDescriptor
import java.nio.file.Path
import java.util.Collections

internal class MutableShellExecOptionsImpl(
  private var _execCommand: ShellExecCommand,
  override val workingDirectory: EelPath,
  private val mutableEnvs: MutableMap<String, String>,
  private val requester: Class<out ShellExecOptionsCustomizer>,
) : MutableShellExecOptions {

  private val translator: TerminalLocalPathTranslator = TerminalLocalPathTranslator(eelDescriptor)

  override fun setEnvironmentVariable(name: String, value: String?) {
    if (value == null) {
      mutableEnvs.remove(name)
      LOG.debug { "$requester removed environment variable '$name'" }
    }
    else {
      mutableEnvs[name] = value
      LOG.debug { "$requester: setEnvironmentVariable('$name', '$value')" }
    }
  }

  override fun setEnvironmentVariableToPath(name: String, path: Path?) {
    if (path == null) {
      mutableEnvs.remove(name)
      LOG.debug { "$requester removed environment variable '$name'" }
    }
    else {
      val translatedPath = translatePathToRemote(path)
      if (translatedPath != null) {
        mutableEnvs[name] = translatedPath
        LOG.debug { "$requester: setEnvironmentVariableToPath('$name', '$translatedPath')" }
      }
      else {
        LOG.debug { "$requester: path translation failed for setEnvironmentVariableToPath('$name', '$path'), skipping" }
      }
    }
  }

  override fun appendEntryToPATH(entry: Path) {
    appendEntryToPathLikeEnv(PATH, entry)
  }

  override fun prependEntryToPATH(entry: Path) {
    prependEntryToPathLikeEnv(PATH, entry)
  }

  override fun appendEntryToPathLikeEnv(envName: String, entry: Path) {
    val remotePath = translatePathToRemote(entry) ?: run {
      LOG.debug { "$requester: appendEntryToPathLikeEnv('$envName', '$entry') failed, skipping" }
      return
    }
    mutableEnvs[envName] = translator.joinEntries(envs[envName].orEmpty(), remotePath)
    LOG.debug { "$requester: appendEntryToPathLikeEnv('$envName', '$remotePath')" }
  }

  override fun prependEntryToPathLikeEnv(envName: String, entry: Path) {
    val remotePath = translatePathToRemote(entry) ?: run {
      LOG.debug { "$requester: prependEntryToPathLikeEnv('$envName', '$entry') failed, skipping" }
      return
    }
    mutableEnvs[envName] = translator.joinEntries(remotePath, envs[envName].orEmpty())
    LOG.debug { "$requester: prependEntryToPathLikeEnv('$envName', '$remotePath')" }
  }

  override val eelDescriptor: EelDescriptor
    get() = workingDirectory.descriptor

  override val execCommand: ShellExecCommand
    get() = _execCommand

  override fun setExecCommand(execCommand: ShellExecCommand) {
    LOG.info("$requester changed shell command from $_execCommand to $execCommand")
    _execCommand = execCommand
  }

  override val envs: Map<String, String> = Collections.unmodifiableMap(mutableEnvs)

  private fun translatePathToRemote(path: Path): String? {
    if (!path.isAbsolute) {
      LOG.debug { "$requester: Relative path '$path' will be added as-is" }
      return path.toString()
    }
    if (eelDescriptor == LocalEelDescriptor) {
      return path.toString()
    }
    return translator.translateAbsoluteLocalPathToRemote(path)?.toString()
  }
}

private const val PATH: String = "PATH"
private val LOG: Logger = logger<MutableShellExecOptionsImpl>()
