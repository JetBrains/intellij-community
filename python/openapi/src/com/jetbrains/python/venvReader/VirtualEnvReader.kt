// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.venvReader

import com.intellij.execution.Platform
import com.intellij.execution.target.FullPathOnTarget
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.environmentVariables
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.localEel
import com.intellij.platform.eel.provider.osFamily
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.ui.EDT
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.PythonHomePath
import com.jetbrains.python.venvReader.VirtualEnvReader.Companion.Instance
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly
import java.io.IOException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.NotDirectoryException
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.isSymbolicLink
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.pathString

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
    return resolveDirFromEnvOrElseGetDirInHomePath(eel, "PYENV_ROOT", PYENV_DEFAULT_DIR_NAME)
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
   * [pathOrDir] is either a direct path to a Python binary or a root directory of python installation or virtualenv
   */
  @RequiresBackgroundThread
  fun findPythonInPythonRoot(pathOrDir: PythonHomePath): PythonBinary? {
    val pythonPattern = getPythonBinaryPattern(pathOrDir.osFamily)
    if (pathOrDir.isRegularFile() && pythonPattern.matches(pathOrDir.name)) {
      return pathOrDir
    }

    if (!pathOrDir.isDirectory()) {
      return null
    }

    val bin = pathOrDir.resolve("bin")
    if (bin.isDirectory()) {
      findInterpreter(bin)?.let { return it }
    }

    val scripts = pathOrDir.resolve("Scripts")
    if (scripts.isDirectory()) {
      findInterpreter(scripts)?.let { return it }
    }

    return findInterpreter(pathOrDir)
  }

  /**
   * [binaryOrDir] is either a venv root or a python binary
   */
  fun findPythonInPythonRootForTarget(binaryOrDir: FullPathOnTarget, platform: Platform): FullPathOnTarget {
    val pythonPattern = getPythonBinaryPattern(platform)
    val separator = platform.fileSeparator
    val binaryOrDirWithoutSeparatorSuffix = binaryOrDir.removeSuffix(separator.toString())
    if (pythonPattern.matches(binaryOrDirWithoutSeparatorSuffix.substringAfterLast(separator))) {
      return binaryOrDir
    }

    return when (platform) {
      Platform.WINDOWS -> "${binaryOrDirWithoutSeparatorSuffix}${separator}Scripts${separator}python.exe"
      Platform.UNIX -> "${binaryOrDirWithoutSeparatorSuffix}${separator}bin${separator}python"
    }
  }

  fun getVenvName(path: Path): String? {
    val bin = path.parent

    val binFolderName = when (forcedOs ?: path.osFamily) {
      EelOsFamily.Posix -> "bin"
      EelOsFamily.Windows -> "Scripts"
    }

    if (bin == null || bin.fileName.pathString != binFolderName) {
      return null
    }

    val venv = bin.parent
    return venv?.name
  }

  fun getVenvNameForTarget(path: FullPathOnTarget, platform: Platform): String? {
    val separator = platform.fileSeparator
    val bin = path.substringBeforeLast(separator)

    val binFolderName = when (platform) {
      Platform.UNIX -> "bin"
      Platform.WINDOWS -> "Scripts"
    }

    if (bin.substringAfterLast(separator) != binFolderName) {
      return null
    }

    val venv = bin.substringBeforeLast(separator)
    return venv.substringAfterLast(separator).takeIf { it.isNotBlank() }
  }

  /**
   * Looks for python binary among directory entries.
   * Prefers the shortest name (e.g., "python" over "python3.12") to ensure consistent results,
   * since virtual environments create multiple symlinks (python, python3, python3.12)
   * and Files.newDirectoryStream() order is undefined.
   */
  @RequiresBackgroundThread
  private fun findInterpreter(dir: Path): PythonBinary? =
    try {
      Files.newDirectoryStream(dir).use { stream ->
        val pythonPattern = getPythonBinaryPattern(forcedOs ?: dir.osFamily)
        
        val candidates = stream.filter {
          it.isRegularFile() && pythonPattern.matches(it.name)
        }.toList()
        
        candidates.minByOrNull { it.name.length }
      }

    }
    catch (_: NotDirectoryException) {
      return null
    }
    catch (_: NoSuchFileException) {
      return null
    }

  @RequiresBackgroundThread
  private fun resolveDirFromEnvOrElseGetDirInHomePath(eel: EelApi?, env: String, dirName: String): Path {
    if (EDT.isCurrentThreadEdt()) {
      // This check should have been done by @RequiresBackgroundThread
      // But since Kotlin doesn't support it, we have to do that imperatively.
      // This error doesn't break user flow but tests
      logger.error("Access from EDT isn't allowed", Throwable())
    }
    val envs = forcedVars
               ?: eel?.let { eel -> runBlockingMaybeCancellable { eel.exec.environmentVariables().eelIt().await() } }
               ?: System.getenv()
    return envs[env]?.let { tryResolvePath(it, eel?.descriptor) }
           ?: (eel?.userInfo?.home?.asNioPath()
               ?: Path(System.getProperty("user.home"))).resolve(dirName)
  }


  companion object {
    private val logger = fileLogger()
    internal val Instance: VirtualEnvReader = VirtualEnvReader(forcedVars = null, forcedOs = null)


    /**
     * We assume this is the default name of the directory that is located in user home and which contains user virtualenv Python
     * environments.
     *
     * @see com.jetbrains.python.sdk.flavors.VirtualEnvSdkFlavor.getDefaultLocation
     */
    const val DEFAULT_VIRTUALENVS_DIR: String = ".virtualenvs"

    @Suppress("VENV_IS_OK") // The only place it should be used in prod
    const val DEFAULT_VIRTUALENV_DIRNAME: String = ".venv"

    const val PYENV_DEFAULT_DIR_NAME: String = ".pyenv"

    private val POSIX_PYTHON_PATTERN = Regex("^(pypy|pythonw?)(\\d+(\\.\\d+)*)?t?$")
    private val WIN_PYTHON_PATTERN = Regex("^(pypy|pythonw?)(\\d+(\\.\\d+)*)?t?\\.exe$", RegexOption.IGNORE_CASE)
    private fun getLocalEelIfApp(): EelApi? = if (ApplicationManager.getApplication() != null) localEel else null

    /**
     * Returns a regex pattern that matches Python binary names.
     * Matches: python, python3, python3.X, python3.X.Y, python3.X.Y.Z, etc., pypy, pypy3, pypy3.X, pypy3.X.Y, etc.
     * (and .exe versions on Windows).
     */
    private fun getPythonBinaryPattern(osFamily: EelOsFamily): Regex {
      return when (osFamily) {
        EelOsFamily.Posix -> POSIX_PYTHON_PATTERN
        EelOsFamily.Windows -> WIN_PYTHON_PATTERN
      }
    }

    private fun getPythonBinaryPattern(platform: Platform): Regex = when (platform) {
      Platform.UNIX -> POSIX_PYTHON_PATTERN
      Platform.WINDOWS -> WIN_PYTHON_PATTERN
    }
  }
}

/**
 * Default (production) instance
 */
@ApiStatus.Internal
fun VirtualEnvReader(): VirtualEnvReader = Instance
