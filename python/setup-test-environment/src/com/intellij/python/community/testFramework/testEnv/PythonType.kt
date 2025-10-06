// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.testFramework.testEnv

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import com.jetbrains.python.venvReader.VirtualEnvReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.NonNls
import java.nio.file.FileSystemException
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.ConcurrentHashMap
import kotlin.Result.Companion.failure
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.pathString
import kotlin.io.path.readAttributes

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
        .map { (path, _) -> path }.also { pythonDirs ->
          // it is ok to access python dirs from tests
          VfsRootAccess.allowRootAccess(ApplicationManager.getApplication(), *pythonDirs.map { it.pathString }.toTypedArray())
        }

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

      withContext(Dispatchers.IO) {
        var pythonBinary: Path? = null
        val err = IllegalStateException("$PYTHON_FOR_TESTS env var points to something that is not a python: $pythonPath")

        try {
          val attrs = pythonPath.readAttributes<BasicFileAttributes>()

          pythonBinary = if (attrs.isDirectory) {
            VirtualEnvReader.Instance.findPythonInPythonRoot(pythonPath)
          }
          else {
            pythonPath
          }
        }
        catch (err2: FileSystemException) {
          err.initCause(err2)

          // Handling a possible reparse point from WindowsApps.
          if (SystemInfo.isWindows && err2.javaClass == FileSystemException::class.java) {
            try {
              if (pythonPath in pythonPath.parent!!.listDirectoryEntries()) {
                pythonBinary = pythonPath
              }
            }
            catch (err3: FileSystemException) {
              err.addSuppressed(err3)
            }
          }
        }

        pythonBinary?.parent ?: throw err
      }
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