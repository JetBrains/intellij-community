// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.venvReader

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.environmentVariables
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.localEel
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.PythonHomePath
import com.jetbrains.python.venvReader.VirtualEnvReader.Companion.Instance
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly
import java.io.IOException
import java.nio.file.NoSuchFileException
import java.nio.file.NotDirectoryException
import java.nio.file.Path
import kotlin.io.path.*

typealias Directory = Path

/**
 * Use [Instance]. Provide "forced" vars to ctor for tests only.
 */
@ApiStatus.Internal
class VirtualEnvReader private constructor(
  private val forcedVars: Map<@NonNls String, @NonNls String>?,
  private val forcedOs: EelOsFamily? = null,
) {

  @TestOnly
  constructor(
    forcedVars: Map<@NonNls String, @NonNls String>? = null,
    isWindows: Boolean? = null,
  ) : this(forcedVars, when (isWindows) {
    true -> EelOsFamily.Windows
    false -> EelOsFamily.Posix
    null -> null
  })

  private constructor() : this(forcedVars = null, forcedOs = null)

  /**
   * Dir with virtual envs
   */
  @RequiresBackgroundThread
  fun getVEnvRootDir(eel: EelApi? = getLocalEelIfApp()): Directory {
    return resolveDirFromEnvOrElseGetDirInHomePath(eel, "WORKON_HOME", DEFAULT_VIRTUALENVS_DIR)
  }

  /**
   * Pythons from virtualenvs
   */
  @RequiresBackgroundThread
  fun findVEnvInterpreters(): List<PythonBinary> =
    findVenvsInDir(getVEnvRootDir())

  @RequiresBackgroundThread
  fun getPyenvRootDir(eel: EelApi? = getLocalEelIfApp()): Directory {
    return resolveDirFromEnvOrElseGetDirInHomePath(eel, "PYENV_ROOT", ".pyenv")
  }

  @RequiresBackgroundThread
  fun findPyenvInterpreters(): List<PythonBinary> =
    findVenvsInDir(getPyenvVersionsDir())

  /**
   * List contents of [root] looking for envs there, returns all pythons it were able to find
   */
  @RequiresBackgroundThread
  fun findVenvsInDir(root: Directory): List<PythonBinary> {

    val candidates: ArrayList<Path> = arrayListOf()
    val children = try {
      root.listDirectoryEntries()
    }
    catch (_: NoSuchFileException) {
      return emptyList()
    }
    catch (_: NotDirectoryException) {
      return emptyList()
    }

    for (dir in children) {
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

    val bin = dir.resolve("bin")
    findInterpreter(bin)?.let { return it }

    val scripts = dir.resolve("Scripts")
    findInterpreter(scripts)?.let { return it }

    return findInterpreter(dir)
  }

  fun getVenvRootPath(path: Path): Path? {
    val bin = path.parent

    val binFolderName = when (forcedOs ?: path.getEelDescriptor().osFamily) {
      EelOsFamily.Posix -> "bin"
      EelOsFamily.Windows -> "Scripts"
    }

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
  private fun findInterpreter(dir: Path): PythonBinary? {
    val pythonNames = when (forcedOs ?: dir.getEelDescriptor().osFamily) {
      EelOsFamily.Posix -> POSIX_BINS
      EelOsFamily.Windows -> WIN_BINS
    }
    return try {
      dir.listDirectoryEntries().firstOrNull { it.name.lowercase() in pythonNames && it.isRegularFile() }
    }
    catch (_: NotDirectoryException) {
      return null
    }
    catch (_: NoSuchFileException) {
      return null
    }
  }

  @RequiresBackgroundThread
  private fun resolveDirFromEnvOrElseGetDirInHomePath(eel: EelApi?, env: String, dirName: String): Path {
    val envs = forcedVars
               ?: eel?.let { eel -> runBlockingMaybeCancellable { eel.exec.environmentVariables().eelIt().await() } }
               ?: System.getenv()
    return envs[env]?.let { tryResolvePath(it, eel?.descriptor) }
           ?: (eel?.userInfo?.home?.asNioPath()
               ?: Path(System.getProperty("user.home"))).resolve(dirName)
  }


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

    @Suppress("VENV_IS_OK") // The only place it should be used in prod
    const val DEFAULT_VIRTUALENV_DIRNAME: String = ".venv"

    private val POSIX_BINS = setOf("pypy", "python")
    private val WIN_BINS = setOf("pypy.exe", "python.exe")
    private fun getLocalEelIfApp(): EelApi? = if (ApplicationManager.getApplication() != null) localEel else null
  }
}