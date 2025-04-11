package com.intellij.python.hatch.service

import com.intellij.platform.eel.fs.EelFileSystemApi
import com.intellij.platform.eel.fs.EelFileSystemApi.ReplaceExistingDuringMove.DO_NOT_REPLACE_DIRECTORIES
import com.intellij.platform.eel.fs.move
import com.intellij.platform.eel.getOr
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.python.hatch.*
import com.intellij.python.hatch.cli.ENV_TYPE_VIRTUAL
import com.intellij.python.hatch.cli.HatchEnvironment
import com.intellij.python.hatch.cli.HatchEnvironments
import com.intellij.python.hatch.runtime.HatchConstants
import com.intellij.python.hatch.runtime.HatchRuntime
import com.intellij.python.hatch.runtime.createHatchRuntime
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

internal class CliBasedHatchService private constructor(
  private val workingDirectoryPath: Path,
  private val hatchRuntime: HatchRuntime,
) : HatchService {
  companion object {
    suspend operator fun invoke(workingDirectoryPath: Path?, hatchExecutablePath: Path? = null, hatchEnvironmentName: String? = null): PyResult<CliBasedHatchService> {
      val envVars = hatchEnvironmentName?.let { mapOf(HatchConstants.AppEnvVars.ENV to it) } ?: emptyMap()
      val hatchRuntime = createHatchRuntime(
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

  override suspend fun isHatchManagedProject(): PyResult<Boolean> {
    val isHatchManaged = withContext(Dispatchers.IO) {
      when {
        workingDirectoryPath.resolve("hatch.toml").exists() -> true
        else -> {
          val pyProjectTomlPath = workingDirectoryPath.resolve("pyproject.toml").takeIf { it.isRegularFile() }
          val hatchRegex = """^\[tool\.hatch\..+]$""".toRegex(RegexOption.MULTILINE)
          pyProjectTomlPath?.readText()?.contains(hatchRegex) == true
        }
      }
    }
    return Result.success(isHatchManaged)
  }


  override suspend fun findVirtualEnvironments(): PyResult<List<HatchVirtualEnvironment>> {
    val hatchEnv = hatchRuntime.hatchCli().env()
    val environments: HatchEnvironments = hatchEnv.show().getOr { return it }
    val virtualEnvironments = environments.getAvailableVirtualHatchEnvironments()

    val available = virtualEnvironments.concurrentMap { env ->
      val pythonHomePath = hatchEnv.find(env.name).getOr { return@concurrentMap null } ?: return@concurrentMap null
      val pythonVirtualEnvironment = hatchRuntime.resolvePythonVirtualEnvironment(pythonHomePath).getOr { return@concurrentMap null }
      HatchVirtualEnvironment(
        hatchEnvironment = env,
        pythonVirtualEnvironment = pythonVirtualEnvironment
      )
    }.filterNotNull()

    return Result.success(available)
  }


  override suspend fun createNewProject(projectName: String): PyResult<ProjectStructure> {
    val eelApi = workingDirectoryPath.getEelDescriptor().upgrade()
    val tempDir = eelApi.fs.createTemporaryDirectory(EelFileSystemApi.CreateTemporaryEntryOptions.Builder().build()).getOr { failure ->
      return Result.failure(FileSystemOperationHatchError(failure.error))
    }

    hatchRuntime.hatchCli().new(projectName, tempDir.asNioPath()).getOr { return it }
    val target = workingDirectoryPath.asEelPath()
    eelApi.fs.move(tempDir, target).replaceExisting(DO_NOT_REPLACE_DIRECTORIES).eelIt().getOr { failure ->
      return Result.failure(FileSystemOperationHatchError(failure.error))
    }

    return Result.success(ProjectStructure(
      sourceRoot = target.asNioPath().resolve("src").takeIf { it.isDirectory() },
      testRoot = target.asNioPath().resolve("tests").takeIf { it.isDirectory() },
    ))
  }

  override suspend fun createVirtualEnvironment(basePythonBinaryPath: PythonBinary?, envName: String?): PyResult<PythonVirtualEnvironment.Existing> {
    val pythonBasedRuntime = basePythonBinaryPath?.let { path ->
      hatchRuntime.withBasePythonBinaryPath(path).getOr { return it }
    } ?: hatchRuntime

    val hatchEnv = pythonBasedRuntime.hatchCli().env()

    hatchEnv.create(envName).getOr { return it }
    val pythonHomePath = hatchEnv.find(envName).getOr { return it }
    val pythonVirtualEnvironment = pythonHomePath?.let { hatchRuntime.resolvePythonVirtualEnvironment(it) }?.getOr { return it }

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