package com.intellij.python.hatch.service

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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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

    private val concurrencyLimit = Semaphore(permits = 5)

    private suspend fun <A, B> Iterable<A>.concurrentMap(f: suspend (A) -> B): List<B> = coroutineScope {
      map {
        async {
          concurrencyLimit.withPermit { f(it) }
        }
      }.awaitAll()
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

  override suspend fun findVirtualEnvironments(): PyResult<List<HatchVirtualEnvironment<P>>> {
    val hatchEnv = hatchCli().env()
    val environments: HatchDetailedEnvironments = hatchEnv.showWithDetails().getOr { return it }
    val virtualEnvironments = environments.toVirtualHatchEnvironments()

    val available = virtualEnvironments.concurrentMap { env ->
      val pythonHomePathOnTarget = hatchEnv.find(env.name).getOr { return@concurrentMap null } ?: return@concurrentMap null
      val pythonHomePath = fileSystem.parsePath(pythonHomePathOnTarget).getOr { return@concurrentMap null }
      val pythonVirtualEnvironment = resolvePythonVirtualEnvironment(fileSystem, pythonHomePath).getOr { return@concurrentMap null }
      HatchVirtualEnvironment(
        hatchEnvironment = env,
        pythonVirtualEnvironment = pythonVirtualEnvironment
      )
    }.filterNotNull()

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
