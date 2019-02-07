// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing

import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.ConfigurationFromContext
import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory

/**
 * Parent of all test configuration producers
 */
abstract class AbstractPythonTestConfigurationProducer<T : AbstractPythonTestRunConfiguration<*>> : RunConfigurationProducer<T> {
  constructor() : super(true)

  /**
   * @deprecated Override [getConfigurationFactory].
   */
  @Deprecated("Override getConfigurationFactory")
  constructor(configurationFactory: ConfigurationFactory) : super(configurationFactory)

  /**
   * Configuration type this producer accepts/creates
   */
  abstract val configurationClass: Class<in T>

  // Some configurations have same type but produces different class
  // to prevent ClassCastException we only allow our configurations
  override fun getConfigurationSettingsList(runManager: RunManager): List<RunnerAndConfigurationSettings> =
    super.getConfigurationSettingsList(runManager).filter { configurationClass.isAssignableFrom(it.configuration.javaClass) }

  override fun createConfigurationFromContext(context: ConfigurationContext?): ConfigurationFromContext? {
    if (context == null || !configurationClass.isAssignableFrom(cloneTemplateConfiguration(context).configuration.javaClass)) {
      return null
    }
    return super.createConfigurationFromContext(context)
  }
}