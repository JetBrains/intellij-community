package com.intellij.python.sdkConfigurator.backend.impl

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.python.pyproject.model.api.SuggestedSdk
import com.intellij.python.pyproject.model.api.autoConfigureSdkIfNeeded
import com.intellij.python.pyproject.model.api.suggestSdk
import com.jetbrains.python.module.PyModuleService
import com.jetbrains.python.orLogException
import com.jetbrains.python.sdk.findPythonSdk
import com.jetbrains.python.sdk.pythonSdk
import com.jetbrains.python.sdk.withSdkConfigurationLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal // Opened for tests only: we can't put tests here because configurators are in communuty.impl
suspend fun configureSdkAutomatically(project: Project): Unit = withContext(Dispatchers.Default) {
  val moduleService = PyModuleService.getInstance(project)
  val pythonModules = ModuleManager.getInstance(project).modules.filter { moduleService.isPythonModule(it) }
  when (pythonModules.size) {
    0 -> return@withContext
    1 -> pythonModules.first().autoConfigureSdkIfNeeded()?.orLogException(logger)
    else -> withSdkConfigurationLock(project) {
      for (module in pythonModules) {
        if (module.findPythonSdk() != null) continue
        val sdkSuggestion = module.suggestSdk()
        when (sdkSuggestion) {
          is SuggestedSdk.SameAs -> {
            val parentSdk = sdkSuggestion.parentModule.findPythonSdk() ?: continue
            module.pythonSdk = parentSdk
          }
          is SuggestedSdk.PyProjectIndependent, null -> {
            logger.trace { "${module.name} skipped in multimodule project autoconfig" }
          }
        }
      }
    }
  }
}


private val logger = fileLogger()
