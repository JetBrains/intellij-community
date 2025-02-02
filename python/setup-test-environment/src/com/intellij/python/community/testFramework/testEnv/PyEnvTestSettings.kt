// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.testFramework.testEnv

import com.intellij.python.community.testFramework.testEnv.PyEnvTestSettings.Companion.PATH_TO_TEST_ENV_PYTHON_INTERPRETERS
import com.intellij.testFramework.UsefulTestCase.IS_UNDER_TEAMCITY
import com.intellij.util.SystemProperties
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.io.File


/**
 * Configures env test environment using env vars and properties.
 * Environment variables are also used in gradle script (setup-test-environment)
 */
@TestOnly
@ApiStatus.Internal
data class PyEnvTestSettings(
  private val folderWithCPythons: String?,
  private val folderWithCondas: String?,
  private val additionalInterpreters: List<File>,
  @get:JvmName("useRemoteSdk")
  val useRemoteSdk: Boolean,
  val isEnvConfiguration: Boolean,
  val isUnderTeamCity: Boolean,
) {
  private val foldersWithPythons: List<File> = listOfNotNull(folderWithCPythons, folderWithCondas).map { File(it) }

  /**
   * Paths to all existing python SDKs (as directories with python installation)
   */
  @TestOnly
  @get:JvmName("getPythons")
  internal val pythons: List<File> = foldersWithPythons
    .filter(File::exists)
    .flatMap { it.listFiles()?.toList() ?: emptyList() }
    .plus(additionalInterpreters)
    .filter { it.isDirectory && !it.name.startsWith('.') }

  /**
   * Configuration in readable format
   */
  fun reportConfiguration() = (PyTestEnvVars.getEnvValues() + listOf(toString())).joinToString("\n")

  companion object {
    // If you decided to change the path, make sure to update it in community/python/setup-test-environment/build.gradle.kts
    private const val PATH_TO_TEST_ENV_PYTHON_INTERPRETERS = "community/python/setup-test-environment/build/pythons"

    /**
     * Tries to resolve [PATH_TO_TEST_ENV_PYTHON_INTERPRETERS] folder from current working dir.
     * It allows automatically detecting the local folder with interpreters built by [community/python/setup-test-environment/build.gradle.kts]
     */
    private fun detectDefaultPyInterpretersFolders(): List<File> {
      var currentFile: File? = File(System.getProperty("user.dir"))
      while (currentFile != null) {
        val pythonInterpretersFolder = currentFile.resolve(PATH_TO_TEST_ENV_PYTHON_INTERPRETERS)
        if (pythonInterpretersFolder.exists()) {
          return pythonInterpretersFolder.listFiles { it.isDirectory && !it.name.startsWith('.') }.toList()
        }
        currentFile = currentFile.parentFile

      }
      return emptyList()
    }

    fun fromEnvVariables(): PyEnvTestSettings {
      val isUnderTeamCity = IS_UNDER_TEAMCITY
      return PyEnvTestSettings(
        folderWithCPythons = PyTestEnvVars.PYCHARM_PYTHONS.getValue(),
        folderWithCondas = PyTestEnvVars.PYCHARM_PYTHON_VIRTUAL_ENVS.getValue(),
        useRemoteSdk = SystemProperties.getBooleanProperty("pycharm.run_remote", false) || PyTestEnvVars.PYCHARM_RUN_REMOTE.isSet(),
        isEnvConfiguration = System.getProperty("pycharm.env") != null
                             || PyTestEnvVars.PYCHARM_ENV.toString() in System.getenv(),
        isUnderTeamCity = isUnderTeamCity,
        additionalInterpreters = PyTestEnvVars.PYCHARM_PYTHON_ENVS.getValue()?.split(File.pathSeparator)
                                   ?.map { File(it) }
                                   ?.toList()
                                 ?: if (isUnderTeamCity) {
                                   emptyList()
                                 }
                                 else {
                                   detectDefaultPyInterpretersFolders()
                                 },
      )
    }
  }
}

/**
 * Env variables used to configure tests
 */
@ApiStatus.Internal
enum class PyTestEnvVars(private val getVarName: (PyTestEnvVars) -> String = { it.name }) {
  /**
   * Path to folder with CPythons
   */
  PYCHARM_PYTHONS,

  /**
   * Path to folder with condas
   */
  PYCHARM_PYTHON_VIRTUAL_ENVS,

  /**
   * [File.separator] separated list of full paths to pythons (including binary) to add to folders found in [PYCHARM_PYTHON_VIRTUAL_ENVS]
   * and [PYCHARM_PYTHONS]
   */
  PYCHARM_PYTHON_ENVS,

  /**
   * Set if launched using "PyEnvTests"
   */
  PYCHARM_ENV,

  /**
   * Only run remote-based tests
   */
  PYCHARM_RUN_REMOTE;


  companion object {
    fun getEnvValues() = entries.map { "$it : ${it.getValue()}" }
  }

  override fun toString() = getVarName(this)

  fun getValue(): String? = System.getenv(toString())

  fun isSet() = getValue() != null
}
