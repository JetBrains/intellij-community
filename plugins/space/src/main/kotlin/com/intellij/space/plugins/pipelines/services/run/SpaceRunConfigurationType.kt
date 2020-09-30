package com.intellij.space.plugins.pipelines.services.run

import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.SimpleConfigurationType
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NotNullLazyValue
import com.intellij.space.messages.SpaceBundle
import icons.SpaceIcons
import platform.common.ProductName

class SpaceRunConfigurationType : SimpleConfigurationType(
  "SpaceRunConfiguration",
  "$ProductName Task",
  SpaceBundle.message("run.configuration.description", ProductName),
  NotNullLazyValue.createValue { SpaceIcons.Main }
) {

  override fun createTemplateConfiguration(project: Project): RunConfiguration {
    return SpaceRunConfiguration(project, this)
  }

  override fun getOptionsClass(): Class<out BaseState>? {
    return SpaceRunTaskConfigurationOptions::class.java
  }
}
