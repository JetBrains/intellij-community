package com.intellij.python.sdkConfigurator.backend.impl

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.rpc.topics.sendToClient
import com.intellij.python.sdkConfigurator.common.impl.ModulesDTO
import com.intellij.python.sdkConfigurator.common.impl.SHOW_SDK_CONFIG_UI_TOPIC
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private val configureMutex = Mutex()

/**
 * Same as [configureSdk] but in a separate coroutine
 */
internal fun configureSdkBg(project: Project, mode: ModuleConfigurationMode) {
  project.service<MyService>().scope.launch(Dispatchers.Default) {
    configureSdk(project, mode)
  }
}

/**
 * Configure SDKs for [project] in [ModuleConfigurationMode] manner (see its doc for the semantics)
 */
internal suspend fun configureSdk(project: Project, mode: ModuleConfigurationMode) {
  withContext(Dispatchers.Default) {
    configureMutex.withLock {
      when (mode) {
        ModuleConfigurationMode.AUTOMATIC -> {
          configureSdkAutomatically(project)
        }
        ModuleConfigurationMode.INTERACTIVE -> {
          val moduleToSuggestedSdk = ModulesSdkConfigurator.create(project)
          val modulesDTO = moduleToSuggestedSdk.modulesDTO
          if (modulesDTO.isNotEmpty()) {
            // No need to send empty list
            SHOW_SDK_CONFIG_UI_TOPIC.sendToClient(project, ModulesDTO(modulesDTO))
          }
        }
      }
    }
  }
}

@Service(Level.PROJECT)
private class MyService(val scope: CoroutineScope)
