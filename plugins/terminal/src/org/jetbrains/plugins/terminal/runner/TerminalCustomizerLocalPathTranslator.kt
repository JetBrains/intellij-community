// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.runner

import com.intellij.execution.wsl.WslPath
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.io.OSAgnosticPathUtil
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelPathBoundDescriptor
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.pathSeparator
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.util.PathUtil
import com.intellij.util.asSafely
import org.jetbrains.plugins.terminal.LocalTerminalCustomizer
import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlin.text.orEmpty

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

  private val multiPathEnvs: List<PathLikeEnv> = capturePathEnvs(MULTI_PATH_ENV_NAMES, envs, descriptor)
  private val singlePathEnvs: List<PathLikeEnv> = capturePathEnvs(SINGLE_PATH_ENV_NAMES, envs, descriptor)

  /**
   * Translates the modifications to the PATH-like environment variables [MULTI_PATH_ENV_NAMES], [SINGLE_PATH_ENV_NAMES]
   * made by [customizerClass].
   */
  fun translate() {
    translateEnvs(multiPathEnvs, ::doTranslate)
    translateEnvs(singlePathEnvs) { _, newValue ->
      translateHostPathToRemote(newValue)
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

  /**
   * Translates new path entries to the format understood by the remote.
   * Changing PATH is a delicate business, so only newly appended or prepended entries
   * are translated, and existing entries remain unchanged.
   *
   * Existing entries are not translated because:
   * 1. Splitting by `LocalEelDescriptor.osFamily.pathSeparator` and then joining them by
   *    `eelDescriptor.osFamily.pathSeparator` can produce invalid entries.
   *    For example, PATH='/foo;bar:/usr/bin' (host: Windows) should not become '/foo:bar:/usr/bin' (remote: Unix).
   * 2. Existing entries are assumed to be already translated to the remote.
   *    No guarantees that re-translating them won't lead to mis-translation in some rare cases.
   * 3. Although performance is not a concern here, the number of all translations can be considerable:
   *    (14 implementations of [LocalTerminalCustomizer] in the monorepo + 10 in external plugins) x
   *    (10-30 entries in PATH) = hundreds of translations, adding a few milliseconds overhead.
   *
   * @return The translated path or `null` if the path was not translated.
   */
  private fun doTranslate(pathLikeEnv: PathLikeEnv, newValue: String): String? {
    val prevValue = pathLikeEnv.prevValue
    val ind = newValue.indexOf(prevValue)
    if (ind >= 0) {
      val prepended = newValue.take(ind)
      val appended = newValue.substring(ind + prevValue.length)
      if (canJoinWithoutSeparator(prepended, prevValue, LocalEelDescriptor) &&
          canJoinWithoutSeparator(prevValue, appended, LocalEelDescriptor)) {
        val remoteEntries = listOf(
          translateHostPathEntriesToRemote(prepended),
          prevValue,
          translateHostPathEntriesToRemote(appended),
        )
        return remoteEntries.reduce { result, entries ->
          joinEntries(result, entries)
        }
      }
    }
    LOG.info("${customizerClass.name} changed ${pathLikeEnv.name} in unexpected way, keeping it as is")
    return null
  }

  private fun translateHostPathEntriesToRemote(pathEntries: String): String {
    val remotePaths = pathEntries.split(LocalEelDescriptor.osFamily.pathSeparator).map {
      translateHostPathToRemote(it)
    }
    return remotePaths.joinToString(separator = descriptor.osFamily.pathSeparator)
  }

  private fun translateHostPathToRemote(pathString: String): String {
    if (pathString.isBlank()) return pathString
    val path: Path = try {
      Path.of(pathString)
    }
    catch (e: InvalidPathException) {
      LOG.debug(e) { "Failed to create Path from $pathString, adding it as is (without translation)" }
      return pathString
    }
    if (!path.isAbsolute) return pathString
    val eelPath: EelPath = try {
      path.asEelPath(descriptor)
    }
    catch (e: Exception) {
      translateWindowsDrivePathToMountedWslPath(path)?.let {
        return it
      }
      translateWslUncPathWithSamePrefix(pathString)?.let {
        return it
      }
      LOG.debug(e) { "Failed to convert $path to EelPath ($descriptor), adding it as is (without translation)" }
      return pathString
    }
    return eelPath.toString()
  }

  /**
   * Translates a Windows path (starting with a drive letter) to its WSL-mounted equivalent.
   *
   *  By default, Windows drives are mounted to `/mnt` in WSL (e.g., `C:\` becomes `/mnt/c/`).
   *  This mount point can be customized via the `automount.root` setting in `/etc/wsl.conf`.
   *  See https://learn.microsoft.com/en-us/windows/wsl/wsl-config
   *
   *  When launching processes via `wsl.exe`, Windows PATH entries are automatically translated
   *  to their WSL-mounted equivalents.
   *  For example, Windows: `C:\Users\user\dir` becomes `/mnt/c/Users/user/dir` in WSL.
   *
   *  This method replicates that translation for scenarios where we're not using `wsl.exe`.
   *
   * @param path The Windows path to be translated.
   * @return The WSL-mounted path if translation succeeds, or null otherwise.
   */
  private fun translateWindowsDrivePathToMountedWslPath(path: Path): String? {
    val wslEelDescriptor = asWslEelDescriptorSafely() ?: return null
    if (OSAgnosticPathUtil.isAbsoluteDosPath(path.toString())) {
      val wslPath = WslPath.parseWindowsUncPath(wslEelDescriptor.rootPath.toString()) ?: run {
        LOG.warn("Failed to parse ${wslEelDescriptor.rootPath} as WSL UNC path")
        return null
      }
      return wslPath.distribution.getWslPath(path)
    }
    return null
  }

  /**
   * Translates a Windows WSL UNC path with prefix different to the prefix of `WslEelDescriptor#rootPath`.
   *
   * For example, it will translate `\\wsl$\Ubuntu\home\user\dir` to `/home/user/dir`
   * for `WslEelDescriptor#rootPath=\\wsl.localhost\Ubuntu`.
   */
  private fun translateWslUncPathWithSamePrefix(pathString: String): String? {
    val wslEelDescriptor = asWslEelDescriptorSafely() ?: return null
    val path = WslPath.parseWindowsUncPath(pathString) ?: return null
    val eelRootPath = WslPath.parseWindowsUncPath(wslEelDescriptor.rootPath.toString()) ?: return null
    if (path.distributionId == eelRootPath.distributionId && path.wslRoot != eelRootPath.wslRoot) {
      val winPathString = PathUtil.toSystemDependentName(pathString)
      check(winPathString.startsWith(path.wslRoot))
      val newPathString = eelRootPath.wslRoot + winPathString.substring(path.wslRoot.length)
      try {
        val newPath = Path.of(newPathString)
        return newPath.asEelPath(descriptor).toString()
      }
      catch (e: Exception) {
        LOG.debug(e) { "Failed to translate $newPathString after changing wsl prefix" }
        return pathString
      }
    }
    return null
  }

  private fun asWslEelDescriptorSafely(): EelPathBoundDescriptor? {
    return descriptor.asSafely<EelPathBoundDescriptor>()?.takeIf {
      it::class.java.name == "com.intellij.platform.ide.impl.wsl.WslEelDescriptor"
    }
  }

  private fun joinEntries(entriesLeft: String, entriesRight: String): String {
    return if (canJoinWithoutSeparator(entriesLeft, entriesRight, descriptor)) {
      entriesLeft + entriesRight
    }
    else {
      entriesLeft + descriptor.osFamily.pathSeparator + entriesRight
    }
  }

  private data class PathLikeEnv(val name: String, val prevValue: String)

  companion object {
    private val MULTI_PATH_ENV_NAMES: List<String> = listOf(
      "PATH",
      "GOPATH",
    ).duplicateWithPrefixes("_INTELLIJ_FORCE_PREPEND_", "_INTELLIJ_FORCE_SET_")

    private val SINGLE_PATH_ENV_NAMES: List<String> = listOf(
      "JEDITERM_SOURCE"
    ) + listOf(
      "GOROOT",
      "GOBIN",
    ).duplicateWithPrefixes("_INTELLIJ_FORCE_SET_")

    private val LOG: Logger = logger<TerminalCustomizerLocalPathTranslator>()

    private fun List<String>.duplicateWithPrefixes(vararg prefixes: String): List<String> {
      return this + prefixes.flatMap { prefix ->
        this.map { prefix + it }
      }
    }

    private fun capturePathEnvs(
      envNamesToCapture: List<String>,
      envs: Map<String, String>,
      descriptor: EelDescriptor,
    ): List<PathLikeEnv> = when (descriptor) {
      LocalEelDescriptor -> emptyList()
      else -> envNamesToCapture.map {
        PathLikeEnv(it, envs[it].orEmpty())
      }
    }

    /**
     * @return if the entries are already separated (can be concatenated as-is)
     */
    private fun canJoinWithoutSeparator(entriesLeft: String, entriesRight: String, descriptor: EelDescriptor): Boolean {
      return entriesLeft.isEmpty() || entriesRight.isEmpty() ||
             entriesLeft.endsWith(descriptor.osFamily.pathSeparator) ||
             entriesRight.startsWith(descriptor.osFamily.pathSeparator)
    }
  }
}
