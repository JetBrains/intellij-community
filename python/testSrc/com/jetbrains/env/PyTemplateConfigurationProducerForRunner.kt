// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.env

import com.intellij.execution.RunManager
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.project.Project
import com.jetbrains.python.run.AbstractPythonRunConfiguration

class PyTemplateConfigurationProducerForRunner<CONF_T : AbstractPythonRunConfiguration<*>>(private val factory: ConfigurationFactory)
  : PyConfigurationProducerForRunner<CONF_T> {

  override fun createConfiguration(project: Project, configurationClass: Class<CONF_T>): CONF_T {
    val configuration = RunManager.getInstance(project).createConfiguration("test", factory).configuration
    assert(configurationClass.isInstance(configuration))
    @Suppress("UNCHECKED_CAST") // checked one line above
    return configuration as CONF_T
  }
}
