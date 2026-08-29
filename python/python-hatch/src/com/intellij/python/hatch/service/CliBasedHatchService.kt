package com.intellij.python.hatch.service

import com.intellij.python.hatch.cli.DEFAULT_ENV_NAME
import com.intellij.openapi.util.io.NioFiles
import com.intellij.platform.eel.fs.EelFileSystemApi
import com.intellij.platform.eel.fs.EelFileUtils
import com.intellij.platform.eel.getOr
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.python.community.execService.UploadConfig
import com.intellij.python.hatch.EnvironmentCreationHatchError
import com.intellij.python.hatch.FileSystemOperationHatchError
import com.intellij.python.hatch.HatchProjectStructureService
import com.intellij.python.hatch.HatchService
import com.intellij.python.hatch.HatchVirtualEnvironment
import com.intellij.python.hatch.ProjectStructure
import com.intellij.python.hatch.PythonVirtualEnvironment
import com.intellij.python.hatch.cli.ENV_TYPE_VIRTUAL
import com.intellij.python.hatch.cli.HatchCli
import com.intellij.python.hatch.cli.HatchDetailedEnvironments
import com.intellij.python.hatch.cli.HatchEnv
import com.intellij.python.hatch.cli.HatchEnvironment
import com.intellij.python.hatch.cli.new
import com.intellij.python.hatch.runtime.HatchConstants
import com.intellij.python.hatch.runtime.createHatchRuntime
import com.intellij.python.hatch.runtime.hatchCli
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.python.pytools.runtime.PyToolRuntime
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.isSuccess
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.PathHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

internal class CliBasedHatchService<P : PathHolder> private constructor(
  private val fileSystem: FileSystem<P>,
  private val workingDirectoryPath: Path,
  private val hatchRuntime: PyToolRuntime,
) : HatchService<P> {
  companion object {
    suspend operator fun <P : PathHolder> invoke(
      fileSystem: FileSystem<P>,
      workingDirectoryPath: Path?,
      hatchExecutablePath: P? = null,
      hatchEnvironmentName: String? = null,
      uploadBeforeExecution: UploadConfig? = null,
    ): PyResult<CliBasedHatchService<P>> {
      val envVars = hatchEnvironmentName?.let { mapOf(HatchConstants.AppEnvVars.ENV to it) } ?: emptyMap()
      val hatchRuntime = createHatchRuntime(
        fileSystem = fileSystem,
        hatchExecutablePath = hatchExecutablePath,
        workingDirectoryPath = workingDirectoryPath,
        envVars = envVars,
        uploadBeforeExecution = uploadBeforeExecution,
      ).getOr { return it }
      return Result.success(CliBasedHatchService(fileSystem, workingDirectoryPath!!, hatchRuntime))
    }

    suspend fun createProjectStructureService(
      fileSystem: FileSystem<PathHolder.Eel>,
      workingDirectoryPath: Path?,
      hatchExecutablePath: PathHolder.Eel? = null,
      uploadBeforeExecution: UploadConfig? = null,
    ): PyResult<HatchProjectStructureService> {
      val hatchService = invoke(
        fileSystem = fileSystem,
        workingDirectoryPath = workingDirectoryPath,
        hatchExecutablePath = hatchExecutablePath,
        uploadBeforeExecution = uploadBeforeExecution,
      ).getOr { return it }
      return Result.success(CliBasedHatchProjectStructureService(hatchService))
    }
  }

  override fun getWorkingDirectoryPath(): Path = workingDirectoryPath

  internal fun hatchCli(): HatchCli<P> = hatchRuntime.hatchCli()

  override suspend fun syncDependencies(envName: String?): PyResult<String> {
    return withContext(Dispatchers.IO) {
      hatchCli().run(envName, "python", "--version")
    }
  }

  override suspend fun removeVirtualEnvironment(envName: String?): PyResult<HatchEnv.RemoveResult> {
    return withContext(Dispatchers.IO) {
      hatchCli().env().remove(envName)
    }
  }

  override suspend fun isHatchManagedProject(): Boolean {
    val isHatchManaged = withContext(Dispatchers.IO) {
      when {
        workingDirectoryPath.resolve("hatch.toml").exists() -> true
        else -> {
          val pyProjectTomlPath = workingDirectoryPath.resolve(PY_PROJECT_TOML).takeIf { it.isRegularFile() }
          val hatchRegex = """^\[tool\.hatch\..+]$""".toRegex(RegexOption.MULTILINE)
          pyProjectTomlPath?.readText()?.contains(hatchRegex) == true
        }
      }
    }
    return isHatchManaged
  }

  /**
   * Every environment the project declares, with where each one lives.
   *
   * hatch is asked twice, whatever the project declares: once for the environments and once for a path. It used to be
   * asked once *per environment* — `hatch env find <name>` for each — which a matrix turns into a process per generated
   * combination, and a modest `[[tool.hatch.envs.test.matrix]]` generates dozens.
   *
   * The one path is enough because hatch keeps them together: `hatch env find` with no argument answers for the default
   * environment, and every other declared environment is its sibling, named by the environment. The default is the one
   * exception to that naming — its own directory carries the *project's* name — and asking for it by omission is what
   * settles it without a second call.
   *
   * Environments hatch keeps for itself live elsewhere, under `.internal`, and would not be found this way. They never
   * reach here: [HatchEnv.showWithDetails] drops them.
   *
   * Where the computed directory holds no interpreter the environment is simply not created yet, which is what the
   * caller wants to know — and it is what [HatchEnv.find] would have reported too, since it computes a location rather
   * than looking for one.
   */
  override suspend fun findVirtualEnvironments(): PyResult<List<HatchVirtualEnvironment<P>>> {
    val hatchEnv = hatchCli().env()
    val environments: HatchDetailedEnvironments = hatchEnv.showWithDetails().getOr { return it }
    val virtualEnvironments = environments.toVirtualHatchEnvironments()
    if (virtualEnvironments.isEmpty()) return Result.success(emptyList())

    val defaultEnvPath = hatchEnv.find().getOr { return it }
                         ?: return Result.success(emptyList())
    // Kept as the target's own path string until the last moment: the environments live on whichever machine hatch ran
    // on, and its separator is the one in the answer it gave.
    val separatorAt = defaultEnvPath.lastIndexOfAny(charArrayOf('/', '\\'))
    if (separatorAt <= 0) return Result.success(emptyList())
    val envsRoot = defaultEnvPath.substring(0, separatorAt)
    val separator = defaultEnvPath[separatorAt]

    val available = virtualEnvironments.map { env ->
      val homeOnTarget = if (env.name == DEFAULT_ENV_NAME) defaultEnvPath else "$envsRoot$separator${env.name}"
      val pythonHomePath = fileSystem.parsePath(homeOnTarget).getOr { return it }
      val pythonVirtualEnvironment = resolvePythonVirtualEnvironment(fileSystem, pythonHomePath).getOr { return it }
      HatchVirtualEnvironment(hatchEnvironment = env, pythonVirtualEnvironment = pythonVirtualEnvironment)
    }

    return Result.success(available)
  }

  override suspend fun findDefaultVirtualEnvironmentOrNull(): PyResult<HatchVirtualEnvironment<P>?> =
    findVirtualEnvironments().mapSuccess { envs -> envs.singleOrNull { it.hatchEnvironment.isDefault() } }

  override suspend fun createVirtualEnvironment(
    basePythonBinaryPath: P?,
    envName: String?,
  ): PyResult<PythonVirtualEnvironment.Existing<P>> {
    val pythonBasedRuntime = basePythonBinaryPath?.let { path ->
      hatchRuntime.withEnv(HatchConstants.AppEnvVars.PYTHON to path.toString())
    } ?: hatchRuntime

    val hatchEnv = pythonBasedRuntime.hatchCli<P>().env()

    hatchEnv.create(envName).getOr { return it }
    val pythonHomePathOnTarget = hatchEnv.find(envName).getOr { return it }
    val pythonHomePath = pythonHomePathOnTarget?.let { rawPath -> fileSystem.parsePath(rawPath).getOr { return it } }
    val pythonVirtualEnvironment = pythonHomePath?.let { resolvePythonVirtualEnvironment(fileSystem, it) }?.getOr { return it }

    val result = when (pythonVirtualEnvironment) {
      is PythonVirtualEnvironment.Existing -> Result.success(pythonVirtualEnvironment)
      else -> Result.failure(EnvironmentCreationHatchError("Hatch didn't create environment but responded with ok"))
    }
    return result
  }
}

private class CliBasedHatchProjectStructureService(
  private val hatchService: CliBasedHatchService<PathHolder.Eel>,
) : HatchProjectStructureService, HatchService<PathHolder.Eel> by hatchService {
  override suspend fun createNewProjectLocally(projectName: String): PyResult<ProjectStructure> {
    val workingDirectoryPath = hatchService.getWorkingDirectoryPath()
    val eelApi = workingDirectoryPath.getEelDescriptor().toEelApi()
    val tempDir = eelApi.fs.createTemporaryDirectory(EelFileSystemApi.CreateTemporaryEntryOptions.Builder().build()).getOr { failure ->
      return Result.failure(FileSystemOperationHatchError(failure.error))
    }

    hatchService.hatchCli().new(projectName, tempDir.asNioPath()).getOr { return it }
    try {
      withContext(Dispatchers.IO) {
        val source = tempDir.asNioPath()
        NioFiles.copyRecursively(source, workingDirectoryPath)
        EelFileUtils.deleteRecursively(source)
      }
    }
    catch (e: IOException) {
      return Result.failure(FileSystemOperationHatchError(e.localizedMessage ?: e.toString()))
    }

    return Result.success(detectLocalProjectStructure(workingDirectoryPath))
  }
}

private fun detectLocalProjectStructure(workingDirectoryPath: Path): ProjectStructure = ProjectStructure(
  sourceRoot = workingDirectoryPath.resolve("src").takeIf { it.isDirectory() },
  testRoot = workingDirectoryPath.resolve("tests").takeIf { it.isDirectory() },
)

/**
 * The virtual environments of a `hatch env show --json` response.
 *
 * The JSON response is the one this service can rely on. It names every matrix environment in full, such as
 * `test.py3.11`, and it never shortens a value. The table response fits itself to the terminal width, so it can report
 * a name as `integration-testing-environme…` and a type as `virtu…`. Both forms then fail: the type no longer equals
 * [ENV_TYPE_VIRTUAL], and Hatch does not know the shortened name.
 *
 * Only the name, the type and the declared [HatchEnvironment.python] reach the caller. The other [HatchEnvironment]
 * fields describe the table columns, and no caller reads them, so this leaves them at their defaults.
 */
private fun HatchDetailedEnvironments.toVirtualHatchEnvironments(): List<HatchEnvironment> =
  filterValues { it.type == ENV_TYPE_VIRTUAL }
    .map { (name, details) ->
      HatchEnvironment(name = name, type = details.type, python = details.python, description = details.description ?: "")
    }

/**
 * Whether the environment at [pythonHomePath] is there, decided by looking rather than by running.
 *
 * An interpreter that exists and can be executed is an existing environment. It used to be started as well, to read a
 * version nobody at this point had asked for — once per declared environment, on every listing. What the version is
 * belongs to whoever wants it; [PythonVirtualEnvironment.Existing.pythonInfo] is left unset here.
 */
private suspend fun <P : PathHolder> resolvePythonVirtualEnvironment(
  fileSystem: FileSystem<P>,
  pythonHomePath: P,
): PyResult<PythonVirtualEnvironment<P>> {
  val binary = fileSystem.resolvePythonBinary(pythonHomePath)?.takeIf { fileSystem.validateExecutable(it).isSuccess }
  val pythonVirtualEnvironment = when (binary) {
    null -> PythonVirtualEnvironment.NotExisting(pythonHomePath)
    else -> PythonVirtualEnvironment.Existing(pythonHomePath)
  }
  return Result.success(pythonVirtualEnvironment)
}
