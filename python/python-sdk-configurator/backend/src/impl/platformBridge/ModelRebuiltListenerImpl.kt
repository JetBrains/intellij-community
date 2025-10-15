package com.intellij.python.sdkConfigurator.backend.impl.platformBridge

import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.project.Project
import com.intellij.python.pyproject.model.api.ModelRebuiltListener
import com.intellij.python.sdkConfigurator.backend.impl.configureSdkAskingUserBg
import com.intellij.python.sdkConfigurator.common.enableSDKAutoConfigurator

internal class ModelRebuiltListenerImpl : ModelRebuiltListener {
  init {
    if (!enableSDKAutoConfigurator) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override fun modelRebuilt(project: Project) {
    configureSdkAskingUserBg(project)
  }
}

