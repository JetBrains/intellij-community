// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DEPRECATION")

package org.jetbrains.plugins.terminal.runner

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.LocalEelDescriptor
import org.jetbrains.plugins.terminal.LocalTerminalCustomizer
import org.jetbrains.plugins.terminal.starter.TerminalLocalPathTranslator
import org.jetbrains.plugins.terminal.starter.TerminalLocalPathTranslator.Companion.MULTI_PATH_ENV_NAMES
import org.jetbrains.plugins.terminal.starter.TerminalLocalPathTranslator.Companion.SINGLE_PATH_ENV_NAMES

/**
 * Fixes potentially incorrect modifications to PATH-like environment variables made by
 * [LocalTerminalCustomizer.customizeCommandAndEnvironment] in non-local scenarios.
 * Since the EP was originally designed for the local scenario, some implementations may
 * still add local/host directories.
 *
 * For example, if `\\wsl.localhost\Ubuntu\home\user\dir` is added to PATH, a Linux
 * process cannot access it when launched with IJEnt (not with `wsl.exe`).
 * This class fixes this issue by converting the WSL UNC path to the Linux path (`/home/user/dir`).
 *
 * In general, the class ensures that added paths are translated to the format understood by the remote.
 *
 * @param descriptor Describes the remote where the shell will be started.
 * @param envs The environment variables map of the remote shell process.
 *             These environment variable values are expected to be in the format understood by the remote, not host.
 * @param customizerClass the class of [LocalTerminalCustomizer]
 */
internal class TerminalCustomizerLocalPathTranslator(
  private val descriptor: EelDescriptor,
  private val envs: MutableMap<String, String>,
  private val customizerClass: Class<out LocalTerminalCustomizer>,
) {

  private val translator: TerminalLocalPathTranslator = TerminalLocalPathTranslator(descriptor)
  private val multiPathEnvs: List<PathLikeEnv> = capturePathEnvs(MULTI_PATH_ENV_NAMES, envs, descriptor)
  private val singlePathEnvs: List<PathLikeEnv> = capturePathEnvs(SINGLE_PATH_ENV_NAMES, envs, descriptor)

  /**
   * Translates the modifications to the PATH-like environment variables [MULTI_PATH_ENV_NAMES], [SINGLE_PATH_ENV_NAMES]
   * made by [customizerClass].
   */
  fun translate() {
    translateEnvs(multiPathEnvs) { pathLikeEnv, newValue ->
      translator.translateMultiPathEnv(pathLikeEnv.name, pathLikeEnv.prevValue, newValue, customizerClass)
    }
    translateEnvs(singlePathEnvs) { _, newValue ->
      translator.translateAbsoluteLocalPathStringToRemote(newValue)
    }
  }

  private fun translateEnvs(pathLikeEnvs: List<PathLikeEnv>, translator: (PathLikeEnv, String) -> String?) {
    for (pathLikeEnv in pathLikeEnvs) {
      val newValue = envs[pathLikeEnv.name]
      if (newValue != null && newValue != pathLikeEnv.prevValue) {
        val translatedValue = translator(pathLikeEnv, newValue)
        if (translatedValue != null) {
          LOG.debug {
            "Translated ${pathLikeEnv.name} for ${customizerClass.name}: ${pathLikeEnv.prevValue} -> $newValue -> $translatedValue"
          }
          envs[pathLikeEnv.name] = translatedValue
        }
      }
    }
  }

  private data class PathLikeEnv(val name: String, val prevValue: String)

  companion object {
    private val LOG: Logger = logger<TerminalCustomizerLocalPathTranslator>()

    private fun capturePathEnvs(
      envNamesToCapture: Set<String>,
      envs: Map<String, String>,
      descriptor: EelDescriptor,
    ): List<PathLikeEnv> = when (descriptor) {
      LocalEelDescriptor -> emptyList()
      else -> envNamesToCapture.map {
        PathLikeEnv(it, envs[it].orEmpty())
      }
    }
  }
}
