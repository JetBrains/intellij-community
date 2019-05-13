// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.env

import com.intellij.testFramework.UsefulTestCase.IS_UNDER_TEAMCITY
import com.intellij.util.SystemProperties
import java.io.File


//TODO: Use Konfig instead?

/**
 * Configures env test environment using env vars and properties.
 * Environment variables are also used in gradle script (setup-test-environment)
 */
internal data class PyEnvTestSettings(
  private val folderWithCPythons: String? = PyTestEnvVars.PYCHARM_PYTHONS.getValue(),
  private val folderWithCondas: String? = PyTestEnvVars.PYCHARM_PYTHON_VIRTUAL_ENVS.getValue(),
  private val foldersWithPythons: List<File> = listOfNotNull(folderWithCPythons, folderWithCondas).map { File(it) },
  private val additionalInterpreters: List<File> = PyTestEnvVars.PYCHARM_PYTHON_ENVS.getValue()?.split(File.pathSeparator)
                                                     ?.map { File(it) }
                                                     ?.toList()
                                                   ?: emptyList(),


  @get:JvmName("isUnderTeamCity")
  val underTeamCity: Boolean = IS_UNDER_TEAMCITY,

  /**
   * Paths to all existing python SDKs
   */
  val pythons: List<File> = foldersWithPythons.filter(File::exists).flatMap { it.listFiles().toList() } + additionalInterpreters,

  /**
   * Enabled when launched with PyEnvTests configuration.
   */
  @get:JvmName("isEnvConfiguration")
  val envConfiguration: Boolean = System.getProperty("pycharm.env") != null
                                  || PyTestEnvVars.PYCHARM_ENV.toString() in System.getenv(),

  /**
   * Only run remote sdk-based tests
   */
  @get:JvmName("useRemoteSdk")
  val useRemoteSdk: Boolean = SystemProperties.getBooleanProperty("pycharm.run_remote", false)
                              || PyTestEnvVars.PYCHARM_RUN_REMOTE.isSet(),

  /**
   * When enabled, only tests marked with "Staging" should run
   */
  @get:JvmName("isStagingMode")
  val stagingMode: Boolean = SystemProperties.getBooleanProperty("pycharm.staging_env", false) ||
                             PyTestEnvVars.PYCHARM_STAGING_ENV.isSet()
) {
  /**
   * Configuration in readable format
   */
  fun reportConfiguration() = (PyTestEnvVars.getEnvValues() + listOf(toString())).joinToString("\n")
}

/**
 * Env variables used to configure tests
 */
private enum class PyTestEnvVars(private val getVarName: (PyTestEnvVars) -> String = { it.name }) {
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
  PYCHARM_RUN_REMOTE,

  /**
   * Only run tests marked with staging
   */
  PYCHARM_STAGING_ENV;

  companion object {
    fun getEnvValues() = values().map { "$it : ${it.getValue()}" }
  }

  override fun toString() = getVarName(this)

  fun getValue(): String? = System.getenv(toString())

  fun isSet() = getValue() != null
}