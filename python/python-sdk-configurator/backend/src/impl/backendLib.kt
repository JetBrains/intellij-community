package com.intellij.python.sdkConfigurator.backend.impl

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.rpc.topics.sendToClient
import com.intellij.python.pyproject.model.api.SuggestedSdk
import com.intellij.python.pyproject.model.api.suggestSdk
import com.intellij.python.sdkConfigurator.common.impl.ModuleName
import com.intellij.python.sdkConfigurator.common.impl.ModulesDTO
import com.intellij.python.sdkConfigurator.common.impl.SHOW_SDK_CONFIG_UI_TOPIC
import com.jetbrains.python.Result
import com.jetbrains.python.sdk.configuration.CreateSdkInfo
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfigurationExtension
import com.jetbrains.python.sdk.getOrCreateAdditionalData
import com.jetbrains.python.sdk.setAssociationToPath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private val askUserMutex = Mutex()

// TODO: DOC
internal fun configureSdkAskingUser(project: Project) {
  project.service<MyService>().scope.launch(Dispatchers.Default) {
    askUserMutex.withLock {
      val moduleToSuggestedSdk = getModulesWithoutSDK(project)
      if (moduleToSuggestedSdk.modules.isNotEmpty()) {
        // No need to send empty list
        SHOW_SDK_CONFIG_UI_TOPIC.sendToClient(project, moduleToSuggestedSdk)
      }
    }
  }
}

/**
 * Configures SDK for modules without SDK in automatic manner trying to fix as many modules as possible.
 * Errors are logged.
 */
internal suspend fun configureSdkAutomatically(project: Project, modulesOnly: Set<ModuleName>? = null) {
  withContext(Dispatchers.Default) {
    val modules = project.modules
      .filter { ModuleRootManager.getInstance(it).sdk == null }
      .filter { modulesOnly == null || it.name in modulesOnly }
    if (modules.isEmpty()) {
      // All modules have SDK
      return@withContext
    }
    val configurators = PyProjectSdkConfigurationExtension.EP_NAME.extensionList
    val configuratorsByTool = configurators
      .mapNotNull { extension -> extension.asPyProjectTomlSdkConfigurationExtension()?.toolId?.let { Pair(it, extension) } }
      .toMap()

    assert(configurators.isNotEmpty()) { "PyCharm can't work without any SDK configurator" }

    val tomlBasedConfigurators = configuratorsByTool.values
    val legacyConfigurators = configurators.filter { it.asPyProjectTomlSdkConfigurationExtension() == null }
    val allSortedConfigurators = tomlBasedConfigurators + legacyConfigurators

    val modulesWithSameSdk = mutableMapOf<Module, Module>()
    for (module in modules) {
      // TODO: Run in parallel
      withBackgroundProgress(project, PySdkConfiguratorBundle.message("intellij.python.sdk.configuring.module", module.name)) {
        when (val r = module.suggestSdk()) {
          null -> {
            // Not a pyproject.toml: try all configurators
            configureSdkForModule(module, allSortedConfigurators, checkForIntention = true)
          }
          is SuggestedSdk.PyProjectIndependent -> {
            val preferredConfigurators = r.preferTools.mapNotNull { configuratorsByTool[it] }
            if (!configureSdkForModule(module, preferredConfigurators, checkForIntention = false)) {
              // For pyproject.toml based -- use pyproject.toml only configs
              configureSdkForModule(module, tomlBasedConfigurators - preferredConfigurators.toSet(), checkForIntention = true)
            }
          }
          is SuggestedSdk.SameAs -> {
            modulesWithSameSdk[module] = r.parentModule
          }
        }
        // Link workspace members with their workspace
        val reportedBrokenModules = mutableSetOf<Module>()
        for ((module, parentModule) in modulesWithSameSdk) {
          val parentSdk = ModuleRootManager.getInstance(parentModule).sdk
          if (parentSdk != null) {
            ModuleRootModificationUtil.setModuleSdk(module, parentSdk)
            // This SDK is shared, no need to associate it
            // TODO: Support association with multiple modules
            if (parentSdk.getOrCreateAdditionalData().associatedModulePath != null) {
              parentSdk.setAssociationToPath(null)
            }
          }
          else {
            if (parentModule != reportedBrokenModules) {
              logger.warn("No sdk for workspace root ${parentModule}, all children will have no SDKs")
            }
            reportedBrokenModules.add(parentModule)
          }
        }
      }
    }
  }
}

private suspend fun getModulesWithoutSDK(project: Project): ModulesDTO =
  ModulesDTO(project.modules.filter { ModuleRootManager.getInstance(it).sdk == null }.associate { module ->
    val parent = when (val r = module.suggestSdk()) {
      null, is SuggestedSdk.PyProjectIndependent -> null
      is SuggestedSdk.SameAs -> r.parentModule
    }
    Pair(module.name, parent?.name)
  })

private suspend fun configureSdkForModule(module: Module, configurators: List<PyProjectSdkConfigurationExtension>, checkForIntention: Boolean): Boolean {
  // TODO: Parallelize call to checkEnvironmentAndPrepareSdkCreator
  val createSdkInfos = configurators.mapNotNull {
    if (checkForIntention) it.checkEnvironmentAndPrepareSdkCreator(module)
    else it.asPyProjectTomlSdkConfigurationExtension()?.createSdkWithoutPyProjectTomlChecks(module)
  }.sorted()

  for (createSdkInfo in createSdkInfos) {
    val created = when (val r = createSdkInfo.sdkCreator(false)) {
      is Result.Failure -> {
        val msgExtraInfo = when (createSdkInfo) {
          is CreateSdkInfo.ExistingEnv -> " using existing environment "
          is CreateSdkInfo.WillCreateEnv -> " "
        }
        logger.warn("can't create SDK${msgExtraInfo}for ${module.name}: ${r.error.message}")
        false
      }
      is Result.Success -> r.result?.also { sdk ->
        ModuleRootModificationUtil.setModuleSdk(module, sdk)
      } != null
    }
    if (created) {
      return true
    }
  }
  return false
}

private val logger = fileLogger()


@Service(Level.PROJECT)
private class MyService(val scope: CoroutineScope)