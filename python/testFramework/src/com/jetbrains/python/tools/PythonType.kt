// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.tools

import com.intellij.execution.processTools.getResultStdout
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.failure
import com.jetbrains.python.packaging.findCondaExecutableRelativeToEnv
import com.jetbrains.python.sdk.VirtualEnvReader
import com.jetbrains.python.sdk.conda.TargetEnvironmentRequestCommandExecutor
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnv
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnvIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.NonNls
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * Gradle script installs two types of python: conda and vanilla. Env could be obtained by [getTestEnvironment] which also provides closable
 * to clean up after the usage
 */
sealed class PythonType<T : Any>(private val tag: @NonNls String? = null) {
  private companion object {
    val cache: MutableMap<Set<String>, List<Path>> = ConcurrentHashMap()
  }

  /**
   * Returns all test environments ordered from newest (highest) to oldest: each must be closed after the test.
   * If in doubt, take first
   */
  suspend fun getTestEnvironments(vararg additionalTags: @NonNls String): Flow<Pair<T, AutoCloseable>> {
    val key = setOf(*additionalTags)
    val pythons = cache.getOrPut(key) {
      PyEnvTestSettings
        .fromEnvVariables()
        .pythons
        .map { it.toPath() }
        .mapNotNull { path ->
          val binary = VirtualEnvReader.Instance.findPythonInPythonRoot(path)
                       ?: error("No python in $path")
          val flavor = PythonSdkFlavor.tryDetectFlavorByLocalPath(binary.toString())
                       ?: error("Unknown flavor: $binary")
          flavor.getVersionString(binary.toString())?.let { path to PythonSdkFlavor.getLanguageLevelFromVersionStringStatic(it) }
          ?: error("Can't get language level for $flavor , $binary")
        }
        .sortedByDescending { (_, languageLevel) -> languageLevel }
        .filter { (_, languageLevel) -> languageLevel.isPy3K }
        .map { (path, _) -> path }
    }
    return pythons.asFlow()
      .filter { typeMatchesEnv(it, *additionalTags) }
      .map { envDir ->
        pythonPathToEnvironment(
          VirtualEnvReader.Instance.findPythonInPythonRoot(envDir)
          ?: error("Can't find python binary in $envDir"), envDir) // This is a misconfiguration, hence an error
      }
  }


  /**
   * Returns first (whatever it means) test environment and closable that must be closed after the test
   */
  suspend fun getTestEnvironment(vararg additionalTags: @NonNls String): Result<Pair<T, AutoCloseable>> =
    getTestEnvironments(*additionalTags).firstOrNull()?.let { Result.success(it) }
    ?: failure("No python found. See ${PyEnvTestSettings::class} class for more info")


  protected abstract suspend fun pythonPathToEnvironment(pythonBinary: PythonBinary, envDir: Path): Pair<T, AutoCloseable>


  data object VanillaPython3 : PythonType<PythonBinary>() {
    // Python is directly executable
    override suspend fun pythonPathToEnvironment(pythonBinary: PythonBinary, envDir: Path): Pair<PythonBinary, AutoCloseable> {
      val disposable = Disposer.newDisposable("Python tests disposable for VfsRootAccess")
      // We might have python installation outside the project root, but we still need to have access to it.
      VfsRootAccess.allowRootAccess(disposable, pythonBinary.parent.toString())
      return Pair(pythonBinary, AutoCloseable {
        Disposer.dispose(disposable)
      })
    }
  }


  data object Conda : PythonType<PyCondaEnv>("conda") {

    override suspend fun pythonPathToEnvironment(pythonBinary: PythonBinary, envDir: Path): Pair<PyCondaEnv, AutoCloseable> {
      // First, find python binary, then calculate conda from it as env stores "conda" as a regular env
      val condaPath = findCondaExecutableRelativeToEnv(pythonBinary) ?: error("Conda root $pythonBinary doesn't have conda binary")

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
                logger<Conda>().warn(it)
              }
            }
          }
        }
      }
      return Pair(PyCondaEnv(PyCondaEnvIdentity.UnnamedEnv(envDir.toString(), isBase = true), condaPath.toString()), cleanupCondas)
    }

    private suspend fun getCondaNames(condaPath: Path) =
      PyCondaEnv.getEnvs(TargetEnvironmentRequestCommandExecutor(LocalTargetEnvironmentRequest()),
                         condaPath.toString()).getOrThrow()
        .map { it.envIdentity.userReadableName }
        .toMutableSet()
  }


  @RequiresBackgroundThread
  private fun typeMatchesEnv(env: Path, vararg additionalTags: @NonNls String): Boolean {
    val envTags = loadEnvTags(env)

    for (badTag in PythonType::class.sealedSubclasses.filterNot { it.isInstance(this) }.map { it.objectInstance!!.tag }) {
      if (badTag != null && badTag in envTags) return false
    }
    return (tag == null || tag in envTags) && additionalTags.all { it in envTags }
  }
}