// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.startup

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.pathSeparator
import com.intellij.platform.eel.provider.LocalEelDescriptor
import org.jetbrains.plugins.terminal.util.ShellIntegration
import java.nio.file.Path
import java.util.Collections

internal class MutableShellExecOptionsImpl(
  private var _execCommand: ShellExecCommand,
  override val workingDirectory: EelPath,
  private val mutableEnvs: MutableMap<String, String>,
  shellIntegration: ShellIntegration?,
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
    var value = translator.joinEntries(remotePath, envs[envName].orEmpty())
    if (value.startsWith(INTELLIJ_FORCE_PREPEND_PREFIX) && !value.endsWith(eelDescriptor.osFamily.pathSeparator)) {
      // For every `_INTELLIJ_FORCE_PREPEND_FOO=BAR`, we run `export FOO=BAR$FOO`.
      // Therefore, `BAR` should end with the path separator.
      value += eelDescriptor.osFamily.pathSeparator
    }
    mutableEnvs[envName] = value
    LOG.debug { "$requester: prependEntryToPathLikeEnv('$envName', '$remotePath')" }
  }

  override val shellIntegrationConfigurer: ShellIntegrationConfigurer? = shellIntegration?.let {
    ShellIntegrationConfigurerImpl(_execCommand.shellName, mutableEnvs, translator, requester)
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

  override fun toString() = ShellExecOptionsImpl.stringify(_execCommand, workingDirectory, envs)
}

private const val PATH: String = "PATH"
private const val INTELLIJ_FORCE_PREPEND_PREFIX: String = "_INTELLIJ_FORCE_PREPEND_"
private val LOG: Logger = logger<MutableShellExecOptionsImpl>()
