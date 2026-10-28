// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.poetry

import com.intellij.python.community.execService.DownloadConfig
import com.intellij.python.community.execService.EnvVar
import com.intellij.python.community.execService.UploadConfig
import com.intellij.python.community.execService.withVar
import com.intellij.python.community.impl.poetry.backend.PoetryPyTool
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.python.sdk.backend.fileName
import com.intellij.python.sdk.backend.runTool
import com.jetbrains.python.PyInternalExecApi
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.getOrNull
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.venvReader.VirtualEnvReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import kotlin.io.path.isRegularFile

@ApiStatus.Internal
@PyInternalExecApi
const val POETRY_TOML: String = "poetry.toml"

private const val POETRY_LOCK = "poetry.lock"
private val POETRY_PROJECT_FILES = listOf(PY_PROJECT_TOML, POETRY_LOCK, POETRY_TOML)

/** The poetry files of a project, downloaded back from a target after a command that may change them. */
@ApiStatus.Internal
val POETRY_PROJECT_DOWNLOAD_CONFIG: DownloadConfig = DownloadConfig(relativePaths = POETRY_PROJECT_FILES)

/**
 * The poetry files of the project in [projectPath] to upload before a command runs on the machine of [fileSystem], or
 * `null` on the local machine, where poetry reads them in place.
 */
@ApiStatus.Internal
fun <P : PathHolder> poetryMetadataUploadConfig(projectPath: Path, fileSystem: FileSystem<P>): UploadConfig? =
  if (fileSystem.isLocal) null
  else UploadConfig(relativePaths = POETRY_PROJECT_FILES.filter { projectPath.resolve(it).isRegularFile() })

/**
 * Runs poetry with [args] in [projectPath] on the machine of [fileSystem]. [inProjectEnv] sets
 * `virtualenvs.in-project` for this run only.
 *
 * It also sets `virtualenvs.use-poetry-python`, unless [baseEnv] sets it. Without it, poetry looks for the current
 * environment with the first `python` on `PATH`, even when a command names the Python to use. The IDE has another
 * `PATH` than a terminal, so that `python` can be missing or a stub, such as the Microsoft Store alias on Windows,
 * and the command then fails. The Python of poetry itself always runs.
 */
@ApiStatus.Internal
suspend fun <P : PathHolder> runPoetry(
  fileSystem: FileSystem<P>,
  projectPath: Path?,
  vararg args: String,
  poetryExecutable: P? = null,
  inProjectEnv: Boolean? = null,
  baseEnv: List<EnvVar> = emptyList(),
  uploadConfig: UploadConfig? = null,
  downloadConfig: DownloadConfig? = null,
): PyResult<String> {
  // A default: it comes before baseEnv, so a value in baseEnv wins.
  var env = listOf(EnvVar("POETRY_VIRTUALENVS_USE_POETRY_PYTHON", "true")) + baseEnv
  if (inProjectEnv != null) {
    env = env.withVar("POETRY_VIRTUALENVS_IN_PROJECT", inProjectEnv.toString())
  }
  return fileSystem.runTool(
    executable = PoetryPyTool.getInstance(),
    pathFromSdk = poetryExecutable?.toStringOnRemoteMachine(),
    dirPath = projectPath,
    args = args,
    env = env,
    uploadConfig = uploadConfig,
    downloadConfig = downloadConfig,
  )
}

/**
 * The cache environments of the project in [projectPath] on the machine of [fileSystem], as env-root paths, as
 * `poetry env list --full-path` reports them. It forces `virtualenvs.in-project=false`, as the v2 dialog does, so poetry
 * lists the cache envs even when an in-project `.venv` exists. Otherwise it reports only `.venv`. When poetry fails,
 * the poetry error is the result.
 */
@ApiStatus.Internal
suspend fun <P : PathHolder> poetryCacheEnvRoots(fileSystem: FileSystem<P>, projectPath: Path): PyResult<List<P>> {
  val uploadConfig = withContext(Dispatchers.IO) { poetryMetadataUploadConfig(projectPath, fileSystem) }
  val output = runPoetry(fileSystem, projectPath, "env", "list", "--full-path", inProjectEnv = false, uploadConfig = uploadConfig)
    .getOr { return it }
  return PyResult.success(output.lineSequence()
                            .map { it.removeSuffix("(Activated)").trim() }
                            .filter { it.isNotBlank() }
                            .mapNotNull { fileSystem.parsePath(it).getOrNull() }
                            .toList())
}

/** The cache env among these env roots whose folder name ends with Python [version], or `null` when poetry has none. */
@ApiStatus.Internal
fun <P : PathHolder> List<P>.poetryCacheEnvFor(version: String): P? =
  firstOrNull { poetryCacheEnvVersion(it.fileName) == version }

/**
 * The root of the poetry environment that [envRef] names for the project in [projectPath], on the machine of
 * [fileSystem]: the project's `.venv` for [POETRY_IN_PROJECT_ENV_REF], else the cache environment of that Python
 * version, see [poetryEnvRefOf]. The value is `null` when poetry has no such environment, and a failure when poetry
 * cannot list its environments.
 */
@ApiStatus.Internal
suspend fun <P : PathHolder> poetryEnvRootOf(fileSystem: FileSystem<P>, projectPath: Path, envRef: String): PyResult<P?> =
  if (envRef == POETRY_IN_PROJECT_ENV_REF) PyResult.success(fileSystem.resolveInWorkingDir(projectPath,
                                                                                           VirtualEnvReader.DEFAULT_VIRTUALENV_DIRNAME))
  else poetryCacheEnvRoots(fileSystem, projectPath).mapSuccess { it.poetryCacheEnvFor(poetryCacheEnvRefVersion(envRef)) }
