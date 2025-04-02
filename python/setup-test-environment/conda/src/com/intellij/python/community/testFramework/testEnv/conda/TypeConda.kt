package com.intellij.python.community.testFramework.testEnv.conda

import com.intellij.execution.processTools.getResultStdout
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.community.testFramework.testEnv.PythonType
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.packaging.PyCondaPackageService
import com.jetbrains.python.packaging.findCondaExecutableRelativeToEnv
import com.jetbrains.python.sdk.conda.TargetEnvironmentRequestCommandExecutor
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnv
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnvIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.Path
import kotlin.io.path.pathString

@Internal
data object TypeConda : PythonType<PyCondaEnv>("conda") {
  override suspend fun createSdkFor(env: PyCondaEnv): Sdk =
    env.createSdkFromThisEnv(null, emptyList())

  override suspend fun pythonPathToEnvironment(pythonBinary: PythonBinary, envDir: Path): Pair<PyCondaEnv, AutoCloseable> {
    // First, find python binary, then calculate conda from it as env stores "conda" as a regular env
    val condaPath = findCondaExecutableRelativeToEnv(pythonBinary) ?: error("Conda root $pythonBinary doesn't have conda binary")
    // Save a path to conda because some legacy code might use it instead of a full conda path from additional data
    PyCondaPackageService.onCondaEnvCreated(condaPath.pathString)

    // We'll remove then on close
    val condaEnvsBeforeTest = getCondaNames(condaPath)

    val cleanupCondas = AutoCloseable {
      runBlocking(Dispatchers.IO) {
        val condasToRemove = getCondaNames(condaPath)
        condasToRemove.removeAll(condaEnvsBeforeTest)
        for (envName in condasToRemove) {
          println("Removing $envName")

          for (arg in arrayOf("--name", "-p")) {
            val args = arrayOf(condaPath.toString(), "remove", arg, envName, "--all", "-y")
            Runtime.getRuntime().exec(args).getResultStdout().getOrElse {
              logger<TypeConda>().warn(it)
            }
          }
        }
      }
    }
    return Pair(PyCondaEnv(PyCondaEnvIdentity.UnnamedEnv(envDir.toString(), isBase = true), condaPath.toString()), cleanupCondas)
  }

  private suspend fun getCondaNames(condaPath: Path) =
    PyCondaEnv.Companion.getEnvs(TargetEnvironmentRequestCommandExecutor(LocalTargetEnvironmentRequest()),
                                 condaPath.toString()).getOrThrow()
      .map { it.envIdentity.userReadableName }
      .toMutableSet()
}