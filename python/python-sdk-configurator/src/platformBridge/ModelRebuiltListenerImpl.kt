package com.intellij.python.sdkConfigurator.platformBridge

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.python.pyproject.model.api.ModelRebuiltListener
import com.intellij.python.sdkConfigurator.configureSdkAutomatically
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class ModelRebuiltListenerImpl : ModelRebuiltListener {
  private val mutex = Mutex()
  override fun modelRebuilt(project: Project) {
    project.service<MyService>().scope.launch(Dispatchers.Default) {
      mutex.withLock {
       configureSdkAutomatically(project)
      }
    }
  }
}

@Service(Level.PROJECT)
private class MyService(val scope: CoroutineScope)
