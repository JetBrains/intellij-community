// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.env

import com.intellij.openapi.project.Project
import com.jetbrains.python.run.AbstractPythonRunConfiguration

/**
 * Creates run configuration for tests.
 * See [PyTemplateConfigurationProducerForRunner] to create config using factory
 */
interface PyConfigurationProducerForRunner<CONF_T : AbstractPythonRunConfiguration<*>> {
  fun createConfiguration(project: Project, configurationClass: Class<CONF_T>): CONF_T
}
