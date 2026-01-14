package com.intellij.python.sdkConfigurator.backend.impl.platformBridge

import com.intellij.openapi.project.Project
import com.intellij.python.pyproject.model.api.ModelRebuiltListener
import com.intellij.python.sdkConfigurator.backend.impl.ModuleConfigurationMode
import com.intellij.python.sdkConfigurator.backend.impl.configureSdkBg

internal class ModelRebuiltListenerImplToConfigureSdk : ModelRebuiltListener {

  override fun modelRebuilt(project: Project) {
    configureSdkBg(project, mode = ModuleConfigurationMode.AUTOMATIC)
  }
}

