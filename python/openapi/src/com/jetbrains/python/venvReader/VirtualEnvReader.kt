// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.venvReader

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.util.SystemProperties
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.PythonHomePath
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.*

typealias Directory = Path

@ApiStatus.Internal
class VirtualEnvReader(
  private val envs: Map<@NonNls String, @NonNls String> = System.getenv(),
  isWindows: Boolean = SystemInfoRt.isWindows,
) {
  private val pythonNames = if (isWindows)
    setOf("pypy.exe", "python.exe")
  else
    setOf("pypy", "python")

  private val binFolderName = if (isWindows) {
    "Scripts"
  } else {
    "bin"
  }

  /**
   * Dir with virtual envs
   */
  @RequiresBackgroundThread
  fun getVEnvRootDir(): Directory {
    return resolveDirFromEnvOrElseGetDirInHomePath("WORKON_HOME", DEFAULT_VIRTUALENVS_DIR)
  }

  /**
   * Pythons from virtualenvs
   */
  @RequiresBackgroundThread
  fun findVEnvInterpreters(): List<PythonBinary> =
    findLocalInterpreters(getVEnvRootDir())

  @RequiresBackgroundThread
  fun getPyenvRootDir(): Directory {
    return resolveDirFromEnvOrElseGetDirInHomePath("PYENV_ROOT", ".pyenv")
  }

  @RequiresBackgroundThread
  fun findPyenvInterpreters(): List<PythonBinary> =
    findLocalInterpreters(getPyenvVersionsDir())

  @RequiresBackgroundThread
  fun findLocalInterpreters(root: Directory): List<PythonBinary> {
    if (!root.isDirectory()) {
      return listOf()
    }

    val candidates: ArrayList<Path> = arrayListOf()
    for (dir in root.listDirectoryEntries()) {
      findPythonInPythonRoot(dir)?.let { candidates.add(it) }
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
  private fun getPyenvVersionsDir(): Directory {
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


  /**
   * [dir] is root directory of python installation or virtualenv
   */
  @RequiresBackgroundThread
  fun findPythonInPythonRoot(dir: PythonHomePath): PythonBinary? {
    if (!dir.isDirectory()) {
      return null
    }

    val bin = dir.resolve("bin")
    if (bin.isDirectory()) {
      findInterpreter(bin)?.let { return it }
    }

    val scripts = dir.resolve("Scripts")
    if (scripts.isDirectory()) {
      findInterpreter(scripts)?.let { return it }
    }

    return findInterpreter(dir)
  }

  fun getVenvRootPath(path: Path): Path? {
    val bin = path.parent

    if (bin == null || bin.fileName.pathString != binFolderName) {
      return null
    }

    val venv = bin.parent

    if (venv == null) {
      return null
    }

    val root = venv.parent

    if (root == null) {
      return null
    }

    return root
  }

  /**
   * Looks for python binary among directory entries
   */
  @RequiresBackgroundThread
  private fun findInterpreter(dir: Path): PythonBinary? =
    dir.listDirectoryEntries().firstOrNull { it.isRegularFile() && it.name.lowercase() in pythonNames }

  @RequiresBackgroundThread
  private fun resolveDirFromEnvOrElseGetDirInHomePath(env: String, dirName: String): Path =
    envs[env]?.let { tryResolvePath(it) }
    ?: Path.of(SystemProperties.getUserHome(), dirName)


  companion object {
    @JvmStatic
    val Instance: VirtualEnvReader = VirtualEnvReader()


    /**
     * We assume this is the default name of the directory that is located in user home and which contains user virtualenv Python
     * environments.
     *
     * @see com.jetbrains.python.sdk.flavors.VirtualEnvSdkFlavor.getDefaultLocation
     */
    const val DEFAULT_VIRTUALENVS_DIR: String = ".virtualenvs"
    const val DEFAULT_VIRTUALENV_DIRNAME: String = ".venv"

  }
}