// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.testFramework.testEnv

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import com.jetbrains.python.venvReader.VirtualEnvReader
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.NonNls
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.Result.Companion.failure
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

/**
 * Gradle script installs two types of python: conda and vanilla. Env could be obtained by [createSdkClosableEnv] which also provides closable
 * to clean up after the usage
 */
abstract class PythonType<T : Any>(private val tag: @NonNls String) {
  companion object {
    private val cache: MutableMap<Set<String>, List<Path>> = ConcurrentHashMap()
    private const val PYTHON_FOR_TESTS: String = "PYTHON_FOR_TESTS"
    private val customPython: String? get() = System.getenv(PYTHON_FOR_TESTS)
    val BUILD_KTS_MESSAGE: String = "`build.gradle.kts` from the same module ${PythonType::class} sits in. Be sure to read it first: you will need to run `gradle build` there"
    val customPythonMessage: String? get() = customPython?.let { "You are using custom python $it set by $PYTHON_FOR_TESTS env var" }
  }

  /**
   * Returns all test environments ordered from newest (highest) to oldest: each must be closed after the test.
   * If in doubt, take first
   */
  suspend fun getTestEnvironments(vararg additionalTags: @NonNls String, ensureAtLeastOnePython: Boolean = false): Flow<Pair<T, AutoCloseable>> {
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
        .map { (path, _) -> path }
    }.toMutableList()
    val customPythonDir = if (pythons.isEmpty() && ensureAtLeastOnePython) {
      val pythonStr = customPython
      if (pythonStr == null) {
        error("""
          To run this test you need a python interpreter. You have two options:
          
          1. Use $BUILD_KTS_MESSAGE.
          2. Set env variable $PYTHON_FOR_TESTS to the executable python file you already have, i.e: `/bin/python3` or `c:\python\python.exe`
        """.trimIndent())
      }
      val pythonPath = Path.of(pythonStr)
      val pythonBinary = when {
                           pythonPath.isDirectory() -> VirtualEnvReader.Instance.findPythonInPythonRoot(pythonPath)
                           pythonPath.exists() -> pythonPath
                           else -> null
                         } ?: error("$PYTHON_FOR_TESTS env var points to something that is not a python: $pythonPath")
      pythonBinary.parent
    }
    else {
      null
    }
    if (customPythonDir != null) {
      pythons.add(customPythonDir)
    }
    return pythons.asFlow()
      .filter { it == customPythonDir || typeMatchesEnv(it, *additionalTags) }
      .map { envDir ->
        pythonPathToEnvironment(
          VirtualEnvReader.Instance.findPythonInPythonRoot(envDir)
          ?: error("Can't find python binary in $envDir"), envDir) // This is a misconfiguration, hence an error
      }
  }


  /**
   * Returns sdk, (whatever it means) test environment and closable that must be closed after the test
   */
  suspend fun createSdkClosableEnv(vararg additionalTags: @NonNls String): Result<Triple<Sdk, AutoCloseable, T>> =
    getTestEnvironments(*additionalTags, ensureAtLeastOnePython = true).firstOrNull()?.let { (env, closable) ->
      Result.success(Triple(createSdkFor(env), closable, env))
    }
    ?: failure(AssertionError("No python found. See ${PyEnvTestSettings::class} class for more info"))

  protected abstract suspend fun createSdkFor(t: T): Sdk


  protected abstract suspend fun pythonPathToEnvironment(pythonBinary: PythonBinary, envDir: Path): Pair<T, AutoCloseable>


  @RequiresBackgroundThread
  private fun typeMatchesEnv(env: Path, vararg additionalTags: @NonNls String): Boolean {
    val envTags = loadEnvTags(env)
    return tag in envTags && additionalTags.all { it in envTags }
  }
}