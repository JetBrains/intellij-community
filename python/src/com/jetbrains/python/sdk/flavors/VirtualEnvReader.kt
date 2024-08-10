// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.flavors

import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.util.SystemProperties
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.sdk.add.target.PyAddVirtualEnvPanel.Companion.DEFAULT_VIRTUALENVS_DIR
import com.jetbrains.python.sdk.tryResolvePath
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.isSymbolicLink
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

@IntellijInternalApi
@ApiStatus.Internal
class VirtualEnvReader(private val envs: Map<@NonNls String, @NonNls String> = System.getenv(), isWindows: Boolean = SystemInfoRt.isWindows) {
  private val pythonNames = if (isWindows)
    setOf("pypy.exe", "python.exe")
  else
    setOf("pypy", "python")

  @RequiresBackgroundThread
  fun getVEnvRootDir(): Path {
    return resolveDir("WORKON_HOME", DEFAULT_VIRTUALENVS_DIR)
  }

  @RequiresBackgroundThread
  fun findVEnvInterpreters(): List<Path> =
    findLocalInterpreters(getVEnvRootDir())

  @RequiresBackgroundThread
  fun getPyenvRootDir(): Path {
    return resolveDir("PYENV_ROOT", ".pyenv")
  }

  @RequiresBackgroundThread
  fun findPyenvInterpreters(): List<Path> =
    findLocalInterpreters(getPyenvVersionsDir())

  @RequiresBackgroundThread
  fun findLocalInterpreters(root: Path): List<Path> {
    if (!root.isDirectory()) {
      return listOf()
    }

    val candidates: ArrayList<Path> = arrayListOf()
    for (dir in root.listDirectoryEntries()) {
      candidates.addAll(findInRootDirectory(dir))
    }

    return candidates
  }

  @RequiresBackgroundThread
  fun isPyenvSdk(path: String?): Boolean {
    if (path.isNullOrEmpty()) {
      return false
    }

    val path = tryResolvePath(path) ?: return false
    return isPyenvSdk(path)
  }

  @RequiresBackgroundThread
  fun isPyenvSdk(path: Path): Boolean {
    val real = tryReadLink(path) ?: return false
    return real.startsWith(getPyenvRootDir().toCanonicalPath())
  }

  @RequiresBackgroundThread
  private fun getPyenvVersionsDir(): Path {
    return getPyenvRootDir().resolve("versions")
  }

  @RequiresBackgroundThread
  private fun tryReadLink(path: Path): Path? {
    try {
      // `toRealPath` throws exception if file doesn't exist
      return if (path.isSymbolicLink()) path.toRealPath() else path
    }
    catch (_: IOException) {
    }
    return null
  }


  @RequiresBackgroundThread
  private fun findInRootDirectory(dir: Path): Collection<Path> {
    if (!dir.isDirectory()) {
      return listOf()
    }

    val candidates: ArrayList<Path> = arrayListOf()

    val bin = dir.resolve("bin")
    if (bin.isDirectory()) {
      findInterpreter(bin)?.let { candidates.add(it) }
    }

    val scripts = dir.resolve("Scripts")
    if (scripts.isDirectory()) {
      findInterpreter(scripts)?.let { candidates.add(it) }
    }

    if (candidates.isEmpty()) {
      findInterpreter(dir)?.let { candidates.add(it) }
    }

    return candidates
  }

  @RequiresBackgroundThread
  private fun findInterpreter(dir: Path): Path? =
    dir.listDirectoryEntries().firstOrNull { it.isRegularFile() && it.name.lowercase() in pythonNames }

  @RequiresBackgroundThread
  private fun resolveDir(env: String, dirName: String): Path =
    envs[env]?.let { tryResolvePath(it) }
    ?: Path.of(SystemProperties.getUserHome(), dirName)



  companion object {
    @JvmStatic
    val Instance = VirtualEnvReader()
  }
}