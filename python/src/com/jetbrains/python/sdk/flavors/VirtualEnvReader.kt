// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.flavors

import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.util.SystemProperties
import com.jetbrains.python.sdk.PythonSdkUtil
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.regex.Pattern
import kotlin.io.path.isDirectory
import kotlin.io.path.isSymbolicLink
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

@IntellijInternalApi
class VirtualEnvReader @JvmOverloads constructor(val envGetter: (String) -> String = ::systemEnvGetter) {
  fun getVEnvRootDir(): Path? {
    return resolveDir("WORKON_HOME", ".virtualenvs")
  }

  fun findVEnvInterpreters(names: Set<String>, pattern: Pattern): List<Path> {
    return findLocalInterpreters(getVEnvRootDir(), names, pattern)
  }

  fun getPyenvRootDir(): Path? {
    return resolveDir("PYENV_ROOT", ".pyenv")
  }

  fun findPyenvInterpreters(names: Set<String>, pattern: Pattern): List<Path> {
    return findLocalInterpreters(getPyenvVersionsDir(), names, pattern)
  }

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

  fun isPyenvSdk(path: String?): Boolean {
    if (path.isNullOrEmpty()) {
      return false;
    }

    return isPyenvSdk(tryResolvePath(path))
  }

  fun isPyenvSdk(path: Path?): Boolean {
    val real = tryReadLink(path)
    if (real == null) {
      return false
    }

    return getPyenvRootDir()?.toCanonicalPath()?.let { real.startsWith(it) } ?: false
  }

  private fun getPyenvVersionsDir(): Path? {
    return getPyenvRootDir()?.resolve("versions")
  }

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

  private fun resolveDir(env: String, dirName: String): Path? {
    val envPath = envGetter(env)
    val path = if (!envPath.isEmpty()) {
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
    catch (ignored: InvalidPathException) {
    }

    return null
  }

  private fun tryReadLink(path: Path?): Path? {
    try {
      if (path?.isSymbolicLink() == true) {
        return path.toRealPath()
      }

      return path
    }
    catch (ignored: Exception) {
    }

    return null
  }

  companion object {
    @JvmStatic
    val Instance = VirtualEnvReader()

    @JvmStatic
    fun systemEnvGetter(name: String): String {
      return System.getenv(name) ?: String()
    }
  }
}