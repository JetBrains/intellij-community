package com.intellij.python.sdkConfigurator.backend.impl

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.python.pyproject.model.api.SuggestedSdk
import com.intellij.python.pyproject.model.api.suggestSdk
import com.intellij.python.sdkConfigurator.common.ModuleName
import com.jetbrains.python.Result
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfigurationExtension
import com.jetbrains.python.sdk.getOrCreateAdditionalData
import com.jetbrains.python.sdk.setAssociationToPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
      .mapNotNull { extension -> extension.toolId?.let { Pair(it, extension) } }
      .toMap()

    assert(configurators.isNotEmpty()) { "PyCharm can't work without any SDK configurator" }

    val tomlBasedConfigurators = configurators.filter { it.toolId != null }
    val legacyConfigurators = configurators.filter { it.toolId == null }
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

private suspend fun configureSdkForModule(module: Module, configurators: List<PyProjectSdkConfigurationExtension>, checkForIntention: Boolean): Boolean {
  for (extension in configurators) {
    if (checkForIntention && extension.getIntention(module) == null) {
      logger.info("${extension.javaClass} skipped for ${module.name}")
      continue
    }
    val created = when (val r = extension.createAndAddSdkForInspection(module)) {
      is Result.Failure -> {
        logger.warn("can't create SDK for ${module.name}: ${r.error.message}")
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