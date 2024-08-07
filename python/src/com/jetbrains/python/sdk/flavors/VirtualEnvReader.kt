// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.flavors

import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.util.SystemProperties
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.sdk.PythonSdkUtil
import com.jetbrains.python.sdk.add.target.PyAddVirtualEnvPanel.Companion.DEFAULT_VIRTUALENVS_DIR
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.io.IOException
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.regex.Pattern
import kotlin.io.path.isDirectory
import kotlin.io.path.isSymbolicLink
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

@IntellijInternalApi
@ApiStatus.Internal
class VirtualEnvReader(private val envs: Map<@NonNls String, @NonNls String> = System.getenv()) {
  @RequiresBackgroundThread
  fun getVEnvRootDir(): Path? {
    return resolveDir("WORKON_HOME", DEFAULT_VIRTUALENVS_DIR)
  }

  @RequiresBackgroundThread
  fun findVEnvInterpreters(names: Set<String>, pattern: Pattern): List<Path> {
    return findLocalInterpreters(getVEnvRootDir(), names, pattern)
  }

  @RequiresBackgroundThread
  fun getPyenvRootDir(): Path? {
    return resolveDir("PYENV_ROOT", ".pyenv")
  }

  @RequiresBackgroundThread
  fun findPyenvInterpreters(names: Set<String>, pattern: Pattern): List<Path> {
    return findLocalInterpreters(getPyenvVersionsDir(), names, pattern)
  }

  @RequiresBackgroundThread
  fun findLocalInterpreters(root: Path?, names: Set<String>, pattern: Pattern): List<Path> {
    if (root == null || !root.isDirectory()) {
      return listOf()
    }

    val candidates: ArrayList<Path> = arrayListOf()
    for (dir in root.listDirectoryEntries()) {
      candidates.addAll(findInRootDirectory(dir, names, pattern))
    }

    return candidates
  }

  @RequiresBackgroundThread
  fun isPyenvSdk(path: String?): Boolean {
    if (path.isNullOrEmpty()) {
      return false
    }

    return isPyenvSdk(tryResolvePath(path))
  }

  @RequiresBackgroundThread
  fun isPyenvSdk(path: Path?): Boolean {
    val real = tryReadLink(path)
    if (real == null) {
      return false
    }

    return getPyenvRootDir()?.toCanonicalPath()?.let { real.startsWith(it) } == true
  }

  @RequiresBackgroundThread
  private fun getPyenvVersionsDir(): Path? {
    return getPyenvRootDir()?.resolve("versions")
  }

  @RequiresBackgroundThread
  private fun findInRootDirectory(dir: Path?, names: Set<String>, pattern: Pattern): Collection<Path> {
    if (dir == null || !dir.isDirectory()) {
      return listOf()
    }

    val candidates: ArrayList<Path> = arrayListOf()

    val bin = dir.resolve("bin")
    if (bin.isDirectory()) {
      findInterpreter(bin, names, pattern)?.let { candidates.add(it) }
    }

    val scripts = dir.resolve("Scripts")
    if (scripts.isDirectory()) {
      findInterpreter(scripts, names, pattern)?.let { candidates.add(it) }
    }

    if (candidates.isEmpty()) {
      findInterpreter(dir, names, pattern)?.let { candidates.add(it) }
    }

    return candidates
  }

  @RequiresBackgroundThread
  private fun findInterpreter(dir: Path, names: Set<String>, pattern: Pattern): Path? {
    for (child in dir.listDirectoryEntries()) {
      if (child.isDirectory()) {
        continue
      }

      val name = child.name.lowercase()
      if (names.contains(name) || pattern.matcher(name).matches()) {
        return child
      }
    }

    return null
  }

  @RequiresBackgroundThread
  private fun resolveDir(env: String, dirName: String): Path? {
    val envPath = envs[env]
    val path = if (!envPath.isNullOrEmpty()) {
      tryResolvePath(envPath)
    }
    else {
      tryResolvePath(SystemProperties.getUserHome())?.resolve(dirName)
    }

    if (path != null && path.isDirectory()) {
      return path
    }

    return null
  }

  private fun tryResolvePath(str: String): Path? {
    if (PythonSdkUtil.isCustomPythonSdkHomePath(str)) return null
    try {
      val path = Paths.get(str)
      return path
    }
    catch (_: InvalidPathException) {
    }

    return null
  }

  @RequiresBackgroundThread
  private fun tryReadLink(path: Path?): Path? {
    try {
      if (path?.isSymbolicLink() == true) {
        return path.toRealPath()
      }

      return path
    }
    catch (_: IOException) {
    }

    return null
  }

  companion object {
    @JvmStatic
    val Instance = VirtualEnvReader()
  }
}