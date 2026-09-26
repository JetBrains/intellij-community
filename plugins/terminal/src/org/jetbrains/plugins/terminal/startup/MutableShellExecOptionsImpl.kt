// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.startup

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.annotations.NativePath
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.pathSeparator
import com.intellij.platform.eel.provider.LocalEelDescriptor
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import java.util.Collections

@ApiStatus.Internal
class MutableShellExecOptionsImpl(
  private var _execCommand: ShellExecCommand,
  override val workingDirectory: EelPath,
  private val mutableEnvs: MutableMap<String, String>,
  private val shellIntegrationAvailable: Boolean,
  private val requester: Class<out ShellExecOptionsCustomizer>,
) : MutableShellExecOptions {

  private val translator: TerminalLocalPathTranslator = TerminalLocalPathTranslator(eelDescriptor)

  override fun setEnvironmentVariable(name: String, value: String?) {
    doSetEnvironmentVariable(name, value, "setEnvironmentVariable")
  }

  override fun setEnvironmentVariableToPath(name: String, path: Path?) {
    if (path == null) {
      doSetEnvironmentVariable(name, null, "setEnvironmentVariableToPath")
    }
    else {
      val translatedPath = translatePathToRemote(path)
      if (translatedPath != null) {
        LOG.debug { "$requester: path translated: '$path' -> '$translatedPath'" }
        doSetEnvironmentVariable(name, translatedPath, "setEnvironmentVariableToPath")
      }
      else {
        LOG.debug { "$requester: path translation failed for setEnvironmentVariableToPath('$name', '$path'), skipping" }
      }
    }
  }

  private fun doSetEnvironmentVariable(name: String, value: String?, context: String) {
    if (value == null) {
      removeEnv(name, context)
      if (shellIntegrationAvailable) {
        removeEnv(name.ensureStartsWith(INTELLIJ_FORCE_SET_PREFIX), context)
      }
    }
    else {
      setEnv(name, value, context)
      if (shellIntegrationAvailable) {
        setEnv(name.ensureStartsWith(INTELLIJ_FORCE_SET_PREFIX), value, context)
      }
    }
  }

  private fun removeEnv(name: String, context: String) {
    val success = mutableEnvs.remove(name) != null
    LOG.debug { "$requester: $context: removed environment variable: $name ($success)" }
  }

  private fun setEnv(name: String, value: String, context: String) {
    mutableEnvs[name] = value
    LOG.debug { "$requester: $context: set environment variable: $name='$value'" }
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
    mutableEnvs[envName] = joinWithPathLikeEnv(remotePath, envName, false)
    LOG.debug { "$requester: appendEntryToPathLikeEnv('$envName', '$remotePath')" }
  }

  override fun prependEntryToPathLikeEnv(envName: String, entry: Path) {
    val remotePath = translatePathToRemote(entry) ?: run {
      LOG.debug { "$requester: prependEntryToPathLikeEnv('$envName', '$entry') failed, skipping" }
      return
    }
    val envName = if (shellIntegrationAvailable) envName.ensureStartsWith(INTELLIJ_FORCE_PREPEND_PREFIX) else envName
    mutableEnvs[envName] = joinWithPathLikeEnv(remotePath, envName, true)
    LOG.debug { "$requester: prependEntryToPathLikeEnv('$envName', '$remotePath')" }
  }

  private fun joinWithPathLikeEnv(remotePath: @NativePath String, envName: String, prepend: Boolean): String {
    var value = if (prepend) {
      translator.joinEntries(remotePath, envs[envName].orEmpty())
    }
    else {
      translator.joinEntries(envs[envName].orEmpty(), remotePath)
    }
    if (envName.startsWith(INTELLIJ_FORCE_PREPEND_PREFIX)) {
      // Ensure a force-prepend env var value ends with a path separator.
      // A trailing path separator is required to simplify further shell integration code:
      // For every `_INTELLIJ_FORCE_PREPEND_FOO=BAR`, we run `export FOO=BAR$FOO`.
      // Therefore, `BAR` should end with the path separator.
      value = value.ensureEndsWith(eelDescriptor.osFamily.pathSeparator)
    }
    return value
  }

  override val shellIntegrationConfigurer: ShellIntegrationConfigurer? = if (shellIntegrationAvailable) {
    ShellIntegrationConfigurerImpl(_execCommand.shellName, mutableEnvs, translator, requester)
  }
  else null

  override val eelDescriptor: EelDescriptor
    get() = workingDirectory.descriptor

  override val execCommand: ShellExecCommand
    get() = _execCommand

  override fun setExecCommand(execCommand: ShellExecCommand) {
    LOG.info("$requester changed shell command from $_execCommand to $execCommand")
    _execCommand = execCommand
  }

  override val envs: Map<String, String> = Collections.unmodifiableMap(mutableEnvs)

  private fun translatePathToRemote(path: Path): @NativePath String? {
    if (!path.isAbsolute) {
      LOG.debug { "$requester: Relative path '$path' will be added as-is" }
      return path.toString()
    }
    if (eelDescriptor == LocalEelDescriptor) {
      return path.toString()
    }
    return translator.translateAbsoluteLocalPathToRemote(path)?.toString()
  }

  override fun toString(): String = ShellExecOptionsImpl.stringify(_execCommand, workingDirectory, envs)
}

private fun String.ensureStartsWith(prefix: String): String = if (this.startsWith(prefix)) this else prefix + this
private fun String.ensureEndsWith(suffix: String): String = if (this.endsWith(suffix)) this else this + suffix

private const val PATH: String = "PATH"
private const val INTELLIJ_FORCE_PREPEND_PREFIX: String = "_INTELLIJ_FORCE_PREPEND_"
private const val INTELLIJ_FORCE_SET_PREFIX: String = "_INTELLIJ_FORCE_SET_"
private val LOG: Logger = logger<MutableShellExecOptionsImpl>()
