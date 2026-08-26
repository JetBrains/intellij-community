package com.intellij.python.sdkConfigurator.backend.impl

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.python.pyproject.model.api.CreateSdkNotFilesResult
import com.intellij.python.pyproject.model.api.SdkConfigurationResult
import com.intellij.python.pyproject.model.api.autoConfigureSdkDoNotCreateFiles
import com.intellij.python.pyproject.model.api.autoConfigureSdkExistingOnly
import com.intellij.python.pyproject.model.api.configureSdkIfNeeded
import com.jetbrains.python.module.PyModuleService
import com.jetbrains.python.sdk.configuration.CreateSdkInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal // Opened for tests only: we can't put tests here because configurators are in communuty.impl
suspend fun configureSdkAutomatically(project: Project): Unit = withContext(Dispatchers.Default) {
  val moduleService = PyModuleService.getInstance(project)
  val pythonModules = ModuleManager.getInstance(project).modules.filter { moduleService.isPythonModule(it) }
  when (pythonModules.size) {
    0 -> Unit
    1 -> {
      val module = pythonModules.first()
      module.configureSdkIfNeeded { autoConfigureSdkDoNotCreateFiles() }?.run {
        log(module) { error ->
          when (error) {
            is CreateSdkNotFilesResult.NoFiles -> "No files found on disk"
            is CreateSdkNotFilesResult.SdkCreationError -> "SDK creation error ${error.error}"
          }
        }
      }
    }
    else -> {
      supervisorScope {
        for (module in pythonModules) {
          // If module is disposed, the coroutine gets cancelled, but we still need to configure other modules
          launch {
            module.configureSdkIfNeeded { autoConfigureSdkExistingOnly() }?.run {
              log(module) { error ->
                when (val r = error.createSdkInfo) {
                  is CreateSdkInfo.ExistingEnv -> "Files exist on disk, but no SDK configured"
                  is CreateSdkInfo.WillCreateEnv -> "Files must be created with ${r.intentionName}"
                }
              }
            }
          }
        }
      }
    }
  }
}

private fun <T : Any> SdkConfigurationResult<T>.log(module: Module, logForConfigError: (err: T) -> @NlsSafe String) {
  val (success, message) = when (this) {
    is SdkConfigurationResult.Configured -> {
      Pair(true, "configured with ${this.sdk}")
    }
    is SdkConfigurationResult.NotConfigured -> {
      Pair(false, logForConfigError(reason))
    }
    is SdkConfigurationResult.ParentHasNoSdk -> {
      Pair(false, "parent module has no SDK")
    }
    is SdkConfigurationResult.ToolNotInstalled -> {
      Pair(false, "no required tool installed: ${this.tool.toolToInstall}")
    }
  }
  logger.debug { "module $module config:${if (success) "success" else "error"}: $message" }
}

private val logger = fileLogger()
