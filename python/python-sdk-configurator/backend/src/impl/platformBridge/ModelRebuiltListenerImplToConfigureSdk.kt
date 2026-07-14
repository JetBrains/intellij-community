package com.intellij.python.sdkConfigurator.backend.impl.platformBridge

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.python.pyproject.model.api.ModelRebuiltListener
import com.intellij.python.sdkConfigurator.backend.impl.configureSdkAutomatically
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val configureMutex = Mutex()

internal class ModelRebuiltListenerImplToConfigureSdk : ModelRebuiltListener {

  override fun modelRebuilt(project: Project) {
    project.service<MyService>().scope.launch(Dispatchers.Default) {
      configureMutex.withLock {
        configureSdkAutomatically(project)
      }
    }
  }
}

@Service(Level.PROJECT)
private class MyService(val scope: CoroutineScope)

