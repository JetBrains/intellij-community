// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.env

import com.intellij.util.SystemProperties
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly


private val IS_UNDER_TEAMCITY: Boolean = System.getenv("TEAMCITY_VERSION") != null

/**
 * Configures env test environment using env vars and properties.
 * Environment variables are also used in gradle script (setup-test-environment)
 */
@TestOnly
@ApiStatus.Internal
data class PyEnvTestSettings(
  @get:JvmName("useRemoteSdk")
  val useRemoteSdk: Boolean,
  val isEnvConfiguration: Boolean,
  val isUnderTeamCity: Boolean,
  val pythonVersion: String?,
) {
  /**
   * Configuration in readable format
   */
  fun reportConfiguration() = (PyTestEnvVars.getEnvValues() + listOf(toString())).joinToString("\n")

  companion object {

    fun fromEnvVariables(): PyEnvTestSettings {
      val isUnderTeamCity = IS_UNDER_TEAMCITY
      return PyEnvTestSettings(
        useRemoteSdk = SystemProperties.getBooleanProperty("pycharm.run_remote", false) || PyTestEnvVars.PYCHARM_RUN_REMOTE.isSet(),
        isEnvConfiguration = System.getProperty("pycharm.env") != null
                             || PyTestEnvVars.PYCHARM_ENV.toString() in System.getenv(),
        isUnderTeamCity = isUnderTeamCity,
        pythonVersion = PyTestEnvVars.PYCHARM_PY_VERSION.getValue()
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
   * Set if launched using "PyEnvTests"
   */
  PYCHARM_ENV,

  /**
   * Only run remote-based tests
   */
  PYCHARM_RUN_REMOTE,

  /**
   * Run only on one PY version
   */
  PYCHARM_PY_VERSION;


  companion object {
    fun getEnvValues() = entries.map { "$it : ${it.getValue()}" }
  }

  override fun toString() = getVarName(this)

  fun getValue(): String? = System.getenv(toString())

  fun isSet() = getValue() != null
}
