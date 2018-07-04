/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.jetbrains.python.testing

import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.ConfigurationFromContext
import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType

/**
 * @author Ilya Kazakevich
 */

/**
 * Parent of all test configuration producers
 */
abstract class AbstractPythonTestConfigurationProducer<T : AbstractPythonTestRunConfiguration<*>>
  : RunConfigurationProducer<T> {
  constructor(configurationFactory: ConfigurationFactory?) : super(configurationFactory)
  constructor(configurationType: ConfigurationType?) : super(configurationType)

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