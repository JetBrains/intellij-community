package com.intellij.python.sdkConfigurator.backend.impl.platformBridge

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.platform.rpc.topics.sendToClient
import com.intellij.python.pyproject.model.api.ModelRebuiltListener
import com.intellij.python.pyproject.model.api.SuggestedSdk
import com.intellij.python.pyproject.model.api.suggestSdk
import com.intellij.python.sdkConfigurator.common.ModulesDTO
import com.intellij.python.sdkConfigurator.common.SHOW_SDK_CONFIG_UI_TOPIC
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class ModelRebuiltListenerImpl : ModelRebuiltListener {
  private val mutex = Mutex()
  override fun modelRebuilt(project: Project) {
    project.service<MyService>().scope.launch(Dispatchers.Default) {
      mutex.withLock {
        // TODO: Extract to lib
        val moduleToSuggestedSdk = project.modules.filter { ModuleRootManager.getInstance(it).sdk == null }.associate { module ->
          val parent = when (val r = module.suggestSdk()) {
            null, is SuggestedSdk.PyProjectIndependent -> null
            is SuggestedSdk.SameAs -> r.parentModule
          }
          Pair(module.name, parent?.name)
        }
        if (moduleToSuggestedSdk.isNotEmpty()) {
          SHOW_SDK_CONFIG_UI_TOPIC.sendToClient(project, ModulesDTO(moduleToSuggestedSdk))
        }
      }
    }
  }
}

@Service(Level.PROJECT)
private class MyService(val scope: CoroutineScope)
