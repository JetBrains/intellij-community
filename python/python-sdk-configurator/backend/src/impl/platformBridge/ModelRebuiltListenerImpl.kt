package com.intellij.python.sdkConfigurator.backend.impl.platformBridge

import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.project.Project
import com.intellij.python.pyproject.model.api.ModelRebuiltListener
import com.intellij.python.sdkConfigurator.backend.impl.configureSdkAskingUser
import com.intellij.python.sdkConfigurator.common.enableSDKAutoConfigurator
import kotlinx.coroutines.sync.Mutex

internal class ModelRebuiltListenerImpl : ModelRebuiltListener {
  init {
    if (!enableSDKAutoConfigurator) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override fun modelRebuilt(project: Project) {
    configureSdkAskingUser(project)
  }
}

