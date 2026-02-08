// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.startup

import com.intellij.execution.wsl.WslPath
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.io.OSAgnosticPathUtil
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelPathBoundDescriptor
import com.intellij.platform.eel.annotations.MultiRoutingFileSystemPath
import com.intellij.platform.eel.annotations.NativePath
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.path.EelPathException
import com.intellij.platform.eel.pathSeparator
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.util.PathUtil
import com.intellij.util.asSafely
import java.nio.file.InvalidPathException
import java.nio.file.Path

internal class TerminalLocalPathTranslator(private val descriptor: EelDescriptor) {

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
   *    (14 implementations of [org.jetbrains.plugins.terminal.LocalTerminalCustomizer] in the monorepo + 10 in external plugins) x
   *    (10-30 entries in PATH) = hundreds of translations, adding a few milliseconds overhead.
   *
   * @return The translated path or `null` if the path was not translated.
   */
  fun translateMultiPathEnv(
    envName: String,
    prevValue: String,
    newValue: String,
    requester: Class<*>,
  ): String? {
    val ind = newValue.indexOf(prevValue)
    if (ind >= 0) {
      val prepended = newValue.take(ind)
      val appended = newValue.substring(ind + prevValue.length)
      if (canJoinWithoutSeparator(prepended, prevValue, LocalEelDescriptor) &&
          canJoinWithoutSeparator(prevValue, appended, LocalEelDescriptor)) {
        val remoteEntries = listOf(
          translateLocalPathEntriesToRemote(prepended),
          prevValue,
          translateLocalPathEntriesToRemote(appended),
        )
        return remoteEntries.reduce { result, entries ->
          joinEntries(result, entries)
        }
      }
    }
    LOG.info("${requester.name} changed $envName in unexpected way, keeping it as is")
    return null
  }

  private fun translateLocalPathEntriesToRemote(localPathEntries: String): String {
    val remotePaths = localPathEntries.split(LocalEelDescriptor.osFamily.pathSeparator).map {
      translateAbsoluteLocalPathStringToRemote(it) ?: it
    }
    return remotePaths.joinToString(separator = descriptor.osFamily.pathSeparator)
  }

  /**
   * Translates an absolute path to a remote path understood by [descriptor].
   *
   * @param absolutePathString The string representing the absolute path.
   * @return The translated native path within [descriptor], or `null` if
   *         [absolutePathString] is not absolute, or it cannot be translated.
   */
  fun translateAbsoluteLocalPathStringToRemote(absolutePathString: @MultiRoutingFileSystemPath String): @NativePath String? {
    if (absolutePathString.isBlank()) return null
    val path: Path = try {
      Path.of(absolutePathString)
    }
    catch (e: InvalidPathException) {
      LOG.debug(e) { "Failed to create Path from $absolutePathString, skipping translation" }
      return null
    }
    return translateAbsoluteLocalPathToRemote(path)?.toString()
  }

  /**
   * Translates an absolute path to a remote path understood by [descriptor].
   *
   * @param absolutePath The absolute path.
   * @return The translated [EelPath] representing the native path within [descriptor], or `null` if
   *         [absolutePath] is not absolute, or it cannot be translated.
   */
  fun translateAbsoluteLocalPathToRemote(absolutePath: @MultiRoutingFileSystemPath Path): EelPath? {
    if (!absolutePath.isAbsolute) {
      LOG.debug { "Failed to translate not absolute $absolutePath, skipping" }
      return null
    }
    try {
      return absolutePath.asEelPath(descriptor)
    }
    catch (e: Exception) {
      translateWindowsDrivePathToMountedWslPath(absolutePath)?.let {
        return toEelPathOrNull(it)
      }
      translateWslUncPathWithSamePrefix(absolutePath.toString())?.let {
        return toEelPathOrNull(it)
      }
      LOG.debug(e) { "Failed to translate $absolutePath to EelPath ($descriptor), skipping" }
      return null
    }
  }

  private fun toEelPathOrNull(remotePathString: String): EelPath? {
    try {
      return EelPath.parse(remotePathString, descriptor)
    }
    catch (e: EelPathException) {
      LOG.warn("Failed to translate ${remotePathString} to EelPath ($descriptor)", e)
      return null
    }
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

  internal fun joinEntries(entriesLeft: String, entriesRight: String): String {
    return if (canJoinWithoutSeparator(entriesLeft, entriesRight, descriptor)) {
      entriesLeft + entriesRight
    }
    else {
      entriesLeft + descriptor.osFamily.pathSeparator + entriesRight
    }
  }

  companion object {
    private val LOG: Logger = logger<TerminalLocalPathTranslator>()

    internal val MULTI_PATH_ENV_NAMES: Set<String> = listOf(
      "PATH",
      "GOPATH",
    ).duplicateWithPrefixes("_INTELLIJ_FORCE_PREPEND_", "_INTELLIJ_FORCE_SET_").toSet()

    internal val SINGLE_PATH_ENV_NAMES: Set<String> = (listOf(
      "JEDITERM_SOURCE"
    ) + listOf(
      "GOROOT",
      "GOBIN",
    ).duplicateWithPrefixes("_INTELLIJ_FORCE_SET_")).toSet()

    private fun List<String>.duplicateWithPrefixes(vararg prefixes: String): List<String> {
      return this + prefixes.flatMap { prefix ->
        this.map { prefix + it }
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
