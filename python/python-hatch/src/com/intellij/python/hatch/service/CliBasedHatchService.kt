package com.intellij.python.hatch.service

import com.intellij.openapi.util.io.NioFiles
import com.intellij.platform.eel.fs.EelFileSystemApi
import com.intellij.platform.eel.fs.EelFileUtils
import com.intellij.platform.eel.getOr
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.python.community.execService.python.validatePythonAndGetInfo
import com.intellij.python.hatch.EnvironmentCreationHatchError
import com.intellij.python.hatch.FileSystemOperationHatchError
import com.intellij.python.hatch.HatchService
import com.intellij.python.hatch.HatchVirtualEnvironment
import com.intellij.python.hatch.ProjectStructure
import com.intellij.python.hatch.PythonVirtualEnvironment
import com.intellij.python.hatch.cli.ENV_TYPE_VIRTUAL
import com.intellij.python.hatch.cli.HatchEnvironment
import com.intellij.python.hatch.cli.HatchEnvironments
import com.intellij.python.hatch.runtime.HatchConstants
import com.intellij.python.hatch.runtime.createHatchRuntime
import com.intellij.python.hatch.runtime.hatchCli
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.python.pytools.runtime.PyToolRuntime
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.PythonHomePath
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.impl.resolvePythonBinary
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

internal class CliBasedHatchService private constructor(
  private val workingDirectoryPath: Path,
  private val hatchRuntime: PyToolRuntime,
) : HatchService {
  companion object {
    suspend operator fun invoke(
      fileSystem: FileSystem<PathHolder.Eel>,
      workingDirectoryPath: Path?,
      hatchExecutablePath: Path? = null,
      hatchEnvironmentName: String? = null,
    ): PyResult<CliBasedHatchService> {
      val envVars = hatchEnvironmentName?.let { mapOf(HatchConstants.AppEnvVars.ENV to it) } ?: emptyMap()
      val hatchRuntime = createHatchRuntime(
        fileSystem = fileSystem,
        hatchExecutablePath = hatchExecutablePath,
        workingDirectoryPath = workingDirectoryPath,
        envVars = envVars
      ).getOr { return it }
      return Result.success(CliBasedHatchService(workingDirectoryPath!!, hatchRuntime))
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

  override suspend fun syncDependencies(envName: String?): PyResult<String> {
    return withContext(Dispatchers.IO) {
      hatchRuntime.hatchCli().run(envName, "python", "--version")
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


  override suspend fun findVirtualEnvironments(): PyResult<List<HatchVirtualEnvironment>> {
    val hatchEnv = hatchRuntime.hatchCli().env()
    val environments: HatchEnvironments = hatchEnv.show().getOr { return it }
    val virtualEnvironments = environments.getAvailableVirtualHatchEnvironments()

    val available = virtualEnvironments.concurrentMap { env ->
      val pythonHomePath = hatchEnv.find(env.name).getOr { return@concurrentMap null } ?: return@concurrentMap null
      val pythonVirtualEnvironment = resolvePythonVirtualEnvironment(pythonHomePath).getOr { return@concurrentMap null }
      HatchVirtualEnvironment(
        hatchEnvironment = env,
        pythonVirtualEnvironment = pythonVirtualEnvironment
      )
    }.filterNotNull()

    return Result.success(available)
  }

  override suspend fun findDefaultVirtualEnvironmentOrNull(): PyResult<HatchVirtualEnvironment?> =
    findVirtualEnvironments().mapSuccess { envs -> envs.singleOrNull { it.hatchEnvironment.isDefault() } }


  override suspend fun createNewProject(projectName: String): PyResult<ProjectStructure> {
    val eelApi = workingDirectoryPath.getEelDescriptor().toEelApi()
    val tempDir = eelApi.fs.createTemporaryDirectory(EelFileSystemApi.CreateTemporaryEntryOptions.Builder().build()).getOr { failure ->
      return Result.failure(FileSystemOperationHatchError(failure.error))
    }

    hatchRuntime.hatchCli().new(projectName, tempDir.asNioPath()).getOr { return it }
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

    return Result.success(ProjectStructure(
      sourceRoot = workingDirectoryPath.resolve("src").takeIf { it.isDirectory() },
      testRoot = workingDirectoryPath.resolve("tests").takeIf { it.isDirectory() },
    ))
  }

  override suspend fun createVirtualEnvironment(
    basePythonBinaryPath: PythonBinary?,
    envName: String?,
  ): PyResult<PythonVirtualEnvironment.Existing> {
    val pythonBasedRuntime = basePythonBinaryPath?.let { path ->
      hatchRuntime.withBasePythonBinaryPath(path, HatchConstants.AppEnvVars.PYTHON).getOr { return it }
    } ?: hatchRuntime

    val hatchEnv = pythonBasedRuntime.hatchCli().env()

    hatchEnv.create(envName).getOr { return it }
    val pythonHomePath = hatchEnv.find(envName).getOr { return it }
    val pythonVirtualEnvironment = pythonHomePath?.let { resolvePythonVirtualEnvironment(it) }?.getOr { return it }

    val result = when (pythonVirtualEnvironment) {
      is PythonVirtualEnvironment.Existing -> Result.success(pythonVirtualEnvironment)
      else -> Result.failure(EnvironmentCreationHatchError("Hatch didn't create environment but responded with ok"))
    }
    return result
  }
}

private fun HatchEnvironments.getAvailableVirtualHatchEnvironments(): List<HatchEnvironment> {
  val matricesFlatted = matrices.flatMap { matrixEnvironment ->
    matrixEnvironment.envs.map { envName ->
      with(matrixEnvironment.hatchEnvironment) {
        HatchEnvironment(
          name = envName,
          type = type,
          features = features,
          dependencies = dependencies,
          environmentVariables = environmentVariables,
          scripts = scripts,
          description = description,
        )
      }
    }
  }
  return (standalone + matricesFlatted).filter { it.type == ENV_TYPE_VIRTUAL }
}

private suspend fun resolvePythonVirtualEnvironment(pythonHomePath: PythonHomePath): PyResult<PythonVirtualEnvironment> {
  val pythonInfo = pythonHomePath.takeIf { it.isDirectory() }?.resolvePythonBinary()?.let { pythonBinaryPath ->
    pythonBinaryPath.validatePythonAndGetInfo().getOr { return it }
  }

  val pythonVirtualEnvironment = when {
    pythonInfo == null -> PythonVirtualEnvironment.NotExisting(pythonHomePath)
    else -> PythonVirtualEnvironment.Existing(pythonHomePath, pythonInfo)
  }
  return Result.success(pythonVirtualEnvironment)
}
