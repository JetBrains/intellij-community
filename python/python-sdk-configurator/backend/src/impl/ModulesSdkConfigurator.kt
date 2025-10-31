package com.intellij.python.sdkConfigurator.backend.impl

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.removeUserData
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.python.common.tools.ToolId
import com.intellij.python.pyproject.model.api.SuggestedSdk
import com.intellij.python.pyproject.model.api.suggestSdk
import com.intellij.python.sdkConfigurator.backend.impl.ModulesSdkConfigurator.Companion.create
import com.intellij.python.sdkConfigurator.backend.impl.ModulesSdkConfigurator.Companion.popModulesSDKConfigurator
import com.intellij.python.sdkConfigurator.common.impl.ModuleDTO
import com.intellij.python.sdkConfigurator.common.impl.ModuleName
import com.jetbrains.python.Result
import com.jetbrains.python.sdk.configuration.CreateSdkInfo
import com.jetbrains.python.sdk.configuration.CreateSdkInfoWithTool
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfigurationExtension
import com.jetbrains.python.sdk.getOrCreateAdditionalData
import com.jetbrains.python.sdk.setAssociationToPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Configures SDK for modules in [project].
 *
 * 1. Create instance with [create]
 * 2. Ask use to choose from [modulesDTO]
 * 3. Provide chosen names to [configureSdks]
 *
 * [create] saves instance in project, so you can get in by [popModulesSDKConfigurator]
 */
internal class ModulesSdkConfigurator private constructor(
  private val project: Project,
  private val modules: Map<ModuleName, ModuleCreateInfo>,
) {

  val modulesDTO: List<ModuleDTO>

  init {
    val children = HashMap<ModuleName, MutableSet<ModuleName>>()

    // Find parents
    for ((moduleName, createInfo) in modules) {
      children.putIfAbsent(moduleName, HashSet())
      when (createInfo) {
        is ModuleCreateInfo.CreateSdkInfoWrapper -> Unit
        is ModuleCreateInfo.SameAs -> {
          children.getOrPut(createInfo.parentModuleName) { HashSet() }.add(moduleName)
        }
      }
    }
    //Map modules
    modulesDTO = modules.mapNotNull { (moduleName, createInfo) ->
      when (createInfo) {
        is ModuleCreateInfo.CreateSdkInfoWrapper -> {
          val version = when (val r = createInfo.createSdkInfo) {
            is CreateSdkInfo.ExistingEnv -> r.version
            is CreateSdkInfo.WillCreateEnv -> null
          }
          ModuleDTO(moduleName, createInfo.toolId.id, version, children[moduleName]!!.toList().sorted())
        }
        is ModuleCreateInfo.SameAs -> null
      }
    }
      .sortedBy { it.name }
      .toList()


  }

  companion object {
    /**
     * Create instance and save in [project]
     */
    suspend fun create(project: Project): ModulesSdkConfigurator = ModulesSdkConfigurator(project, getModulesWithoutSDKCreateInfo(project)).also {
      project.putUserData(key, it)
    }

    /**
     * Get instance from project and **clear it**
     */
    fun Project.popModulesSDKConfigurator(): ModulesSdkConfigurator {
      val instance = getUserData(key)
                     ?: error("No ${ModulesSdkConfigurator::class.java} found in $this. Did you call ${::create} or did you already called this method?")
      removeUserData(key) // Drop prev. usage to prevent leak
      return instance
    }

    private suspend fun getModulesWithoutSDKCreateInfo(project: Project): Map<ModuleName, ModuleCreateInfo> = withBackgroundProgress(project, PySdkConfiguratorBundle.message("intellij.python.sdk.looking")) {
      val tools = PyProjectSdkConfigurationExtension.createMap()
      val limit = Semaphore(permits = Registry.intValue("intellij.python.sdkConfigurator.backend.sdk.parallel"))
      val now = System.currentTimeMillis()
      val resultDef = project.modules.filter { ModuleRootManager.getInstance(it).sdk == null }.map { module ->
        limit.withPermit {
          async {
            val moduleInfo = getModuleInfo(module, tools) ?: return@async null
            Pair(module, moduleInfo)
          }
        }
      }
      val result = resultDef.awaitAll().filterNotNull()
      logger.debug { "SDKs calculated in ${System.currentTimeMillis() - now}ms" }
      result.associate { (module, createInfoAndDTO) ->
        Pair(module.name, createInfoAndDTO)
      }
    }

    private val logger = fileLogger()

    private sealed interface ModuleCreateInfo {
      data class CreateSdkInfoWrapper(val createSdkInfo: CreateSdkInfo, val toolId: ToolId) : ModuleCreateInfo
      data class SameAs(val parentModuleName: ModuleName) : ModuleCreateInfo
    }


    private suspend fun getModuleInfo(module: Module, configuratorsByTool: Map<ToolId, PyProjectSdkConfigurationExtension>): ModuleCreateInfo? = // Save on module level
      when (val r = module.suggestSdk()) {
        is SuggestedSdk.PyProjectIndependent -> {
          val tools = r.preferTools.map { configuratorsByTool[it]!! }
          tools.firstNotNullOfOrNull { tool ->
            val createInfo = (tool.asPyProjectTomlSdkConfigurationExtension()?.createSdkWithoutPyProjectTomlChecks(module)
                              ?: tool.checkEnvironmentAndPrepareSdkCreator(module)) ?: return@firstNotNullOfOrNull null
            CreateSdkInfoWithTool(createInfo, tool.toolId).asDTO()
          }
        }
        is SuggestedSdk.SameAs -> {
          ModuleCreateInfo.SameAs(r.parentModule.name)
        }
        null -> null
      } // No tools or not pyproject.toml at all? Use EP as a fallback
      ?: PyProjectSdkConfigurationExtension.findAllSortedForModule(module).firstOrNull()?.let { CreateSdkInfoWithTool(it.createSdkInfo, it.toolId).asDTO() }


    private fun CreateSdkInfoWithTool.asDTO(): ModuleCreateInfo = ModuleCreateInfo.CreateSdkInfoWrapper(createSdkInfo, toolId)


    /**
     * Key used to store instance in project by [create] to be used by [popModulesSDKConfigurator]
     */
    private val key = Key.create<ModulesSdkConfigurator>("pyModulesSDKConfigurator")
  }

  /**
   * Configures SDK for modules [modulesOnly]
   * Errors are logged.
   *
   */
  suspend fun configureSdks(modulesOnly: Set<ModuleName>) {
    withContext(Dispatchers.Default) {
      val modulesMap = project.modules.associateBy { it.name }
      val modulesWithSameSdk = mutableMapOf<Module, Module>()
      for (module in modulesOnly.map { modulesMap[it] ?: error("No module $it, caller broke the contract") }) { // TODO: Run in parallel
        withBackgroundProgress(project, PySdkConfiguratorBundle.message("intellij.python.sdk.configuring.module", module.name)) {
          val createInfo = (modules[module.name] ?: error("No create info for module $module, caller broke the contract"))
          when (createInfo) {
            is ModuleCreateInfo.CreateSdkInfoWrapper -> {
              when (val r = createInfo.createSdkInfo.sdkCreator(false)) {
                is Result.Failure -> { //TODO: Show SDK creation error?
                  logger.warn("Failed to create SDK for ${module.name}: ${r.error}")
                }
                is Result.Success -> {
                  val sdk = r.result!! // can't be `null` and will be non-null soon
                  ModuleRootModificationUtil.setModuleSdk(module, sdk)
                }
              }
            }
            is ModuleCreateInfo.SameAs -> {
              val parent = modulesMap[createInfo.parentModuleName] ?: error("No parent module named ${createInfo.parentModuleName}")
              modulesWithSameSdk[module] = parent
            }
          }
        } // Link workspace members with their workspace
        val reportedBrokenModules = mutableSetOf<Module>()
        for ((module, parentModule) in modulesWithSameSdk) {
          val parentSdk = ModuleRootManager.getInstance(parentModule).sdk
          if (parentSdk != null) {
            ModuleRootModificationUtil.setModuleSdk(module, parentSdk) // This SDK is shared, no need to associate it
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

