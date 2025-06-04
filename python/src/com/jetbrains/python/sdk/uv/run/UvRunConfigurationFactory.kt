// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.uv.run

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.project.Project

internal class UvRunConfigurationFactory(
  type: ConfigurationType,
) : ConfigurationFactory(type) {
  override fun getId(): String = UV_CONFIGURATION_ID

  override fun createTemplateConfiguration(project: Project): RunConfiguration = UvRunConfiguration(project, this)

  override fun getOptionsClass(): Class<out BaseState?>? = null
}
