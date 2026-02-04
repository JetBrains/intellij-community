// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.starter

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.LocalEelDescriptor
import java.nio.file.Path
import java.util.Collections

internal class ShellExecOptionsImpl(
  override val eelDescriptor: EelDescriptor,
  override val workingDirectory: EelPath,
  initialExecCommand: ShellExecCommand,
  private val mutableEnvs: MutableMap<String, String>,
  private val requester: Class<out ShellCustomizer>,
) : ShellExecOptions {

  private val translator: TerminalLocalPathTranslator = TerminalLocalPathTranslator(eelDescriptor)

  override var execCommand: ShellExecCommand = initialExecCommand
    set(value) {
      LOG.info("$requester changed shell command from $field to $value")
      field = value
    }

  override val envs: Map<String, String> = Collections.unmodifiableMap(mutableEnvs)

  override fun setEnvironmentVariable(name: String, value: String?) {
    if (value == null) {
      mutableEnvs.remove(name)
      LOG.debug { "$requester removed environment variable '$name'" }
    }
    else {
      val translatedValue = translator.translateEnvValue(name, envs[name], value, requester)
      mutableEnvs[name] = translatedValue
      LOG.debug { "$requester: setEnvironmentVariable('$name', '$translatedValue')" }
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
        LOG.debug { "$requester: setEnvironmentVariableToPath('$name', '$path') failed, skipping" }
      }
    }
  }

  override fun prependEntryToPATH(pathEntry: Path) {
    prependEntryToPathLikeEnv(PATH, pathEntry)
  }

  override fun appendEntryToPATH(pathEntry: Path) {
    appendEntryToPathLikeEnv(PATH, pathEntry)
  }

  override fun prependEntryToPathLikeEnv(envName: String, entryPath: Path) {
    val remotePath = translatePathToRemote(entryPath) ?: run {
      LOG.debug { "$requester: prependEntryToPathLikeEnv('$envName', '$entryPath') failed, skipping" }
      return
    }
    mutableEnvs[envName] = translator.joinEntries(remotePath, envs[envName].orEmpty())
    LOG.debug { "$requester: prependEntryToPathLikeEnv('$envName', '$remotePath')" }
  }

  override fun appendEntryToPathLikeEnv(envName: String, entryPath: Path) {
    val remotePath = translatePathToRemote(entryPath) ?: run {
      LOG.debug { "$requester: appendEntryToPathLikeEnv('$envName', '$entryPath') failed, skipping" }
      return
    }
    mutableEnvs[envName] = translator.joinEntries(envs[envName].orEmpty(), remotePath)
    LOG.debug { "$requester: appendEntryToPathLikeEnv('$envName', '$remotePath')" }
  }

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
private val LOG: Logger = logger<ShellExecOptionsImpl>()
