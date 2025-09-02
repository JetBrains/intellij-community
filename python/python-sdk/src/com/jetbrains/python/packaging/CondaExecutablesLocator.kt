@file:JvmName("CondaExecutablesLocator")

package com.jetbrains.python.packaging

import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.util.SystemProperties
import com.jetbrains.python.sdk.PythonSdkUtil
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.div

private val CONDA_DEFAULT_ROOTS = listOf("anaconda", "anaconda3", "miniconda", "miniconda3", "Anaconda", "Anaconda3", "Miniconda",
                                         "Miniconda3")
private const val CONDA_ENVS_DIR = "envs"
private const val CONDA_BAT_NAME = "conda.bat"
private const val CONDA_BINARY_NAME = "conda"
private const val WIN_CONDA_BIN_DIR_NAME = "condabin"
private const val UNIX_CONDA_BIN_DIR_NAME = "bin"
private const val PYTHON_EXE_NAME = "python.exe"
private const val PYTHON_UNIX_BINARY_NAME = "python"
private const val WIN_CONTINUUM_DIR_PATH = "AppData\\Local\\Continuum\\"
private const val WIN_PROGRAM_DATA_PATH = "C:\\ProgramData\\"
private const val WIN_C_ROOT_PATH = "C:\\"
private const val UNIX_OPT_PATH = "/opt/"

private val LOG = Logger.getInstance("#com.jetbrains.python.packaging")

@ApiStatus.Internal

fun getCondaBasePython(systemCondaExecutable: String): String? {
  val condaFile = LocalFileSystem.getInstance().findFileByPath(systemCondaExecutable)
  if (condaFile != null) {
    val condaDir = if (SystemInfo.isWindows) condaFile.parent.parent else condaFile.parent
    val python = condaDir.findChild(getPythonName())
    if (python != null) {
      return python.path
    }
  }
  return null
}

private fun getPythonName(): String = if (SystemInfo.isWindows) PYTHON_EXE_NAME else PYTHON_UNIX_BINARY_NAME

@ApiStatus.Internal

fun findCondaExecutableRelativeToEnv(pyExecutable: Path): Path? {
  if (!Files.exists(pyExecutable)) {
    return null
  }
  val pyExecutableDir = pyExecutable.parent ?: return null
  val isBaseConda = Files.exists(pyExecutableDir / CONDA_ENVS_DIR)
  val condaName: String
  val condaFolder: Path
  if (SystemInfo.isWindows) {
    condaName = CONDA_BAT_NAME
    // On Windows python.exe is directly inside base interpreter/environment directory.
    // On other systems executable normally resides in "bin" subdirectory.
    condaFolder = pyExecutableDir
  }
  else {
    condaName = CONDA_BINARY_NAME
    condaFolder = pyExecutableDir.parent ?: return null
  }

  // XXX Do we still need to support this? When did they drop per-environment conda executable?
  val localCondaName = if (SystemInfo.isWindows && !isBaseConda) CONDA_BAT_NAME else condaName
  val immediateConda = findExecutable(localCondaName, condaFolder)
  if (immediateConda != null) {
    return immediateConda
  }
  val envsDir = condaFolder.parent ?: return null
  if (!isBaseConda && envsDir.fileName?.toString() == CONDA_ENVS_DIR) {
    val envsDirParent = envsDir.parent ?: return null
    return findExecutable(condaName, envsDirParent)
  }
  return null
}

private fun getCondaExecutableByName(condaName: String): Path? {
  val userHome = Path.of(SystemProperties.getUserHome())

  for (root in CONDA_DEFAULT_ROOTS) {
    var condaFolder = userHome / root
    var executableFile = findExecutable(condaName, condaFolder)
    if (executableFile != null) return executableFile

    if (SystemInfo.isWindows) {
      condaFolder = userHome / WIN_CONTINUUM_DIR_PATH / root
      executableFile = findExecutable(condaName, condaFolder)
      if (executableFile != null) return executableFile

      condaFolder = Path.of(WIN_PROGRAM_DATA_PATH, root)
      executableFile = findExecutable(condaName, condaFolder)
      if (executableFile != null) return executableFile

      condaFolder = Path.of(WIN_C_ROOT_PATH, root)
      executableFile = findExecutable(condaName, condaFolder)
      if (executableFile != null) return executableFile
    }
    else {
      condaFolder = Path.of(UNIX_OPT_PATH, root)
      executableFile = findExecutable(condaName, condaFolder)
      if (executableFile != null) return executableFile
    }
  }

  return null
}

private fun findExecutable(condaName: String, condaFolder: Path): Path? {
  val binFolderName = if (SystemInfo.isWindows) WIN_CONDA_BIN_DIR_NAME else UNIX_CONDA_BIN_DIR_NAME
  val bin = condaFolder / binFolderName / condaName
  if (!Files.exists(bin)) return null
  return PythonSdkUtil.getExecutablePath(bin, condaName)
}
internal fun getSystemCondaExecutable(): Path? {
  val condaName = if (SystemInfo.isWindows) CONDA_BAT_NAME else CONDA_BINARY_NAME

  // TODO we need another findInPath() that works with Path-s
  val condaInPath = PathEnvironmentVariableUtil.findInPath(condaName)
  if (condaInPath != null) {
    LOG.info("Using $condaInPath as a conda executable (found in PATH)")
    return condaInPath.toPath()
  }

  val condaInRoots = getCondaExecutableByName(condaName)
  if (condaInRoots != null) {
    LOG.info("Using $condaInRoots as a conda executable (found by visiting possible conda roots)")
    return condaInRoots
  }

  LOG.info("System conda executable is not found")
  return null
}