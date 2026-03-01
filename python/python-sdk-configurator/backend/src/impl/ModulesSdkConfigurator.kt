package com.intellij.python.sdkConfigurator.backend.impl

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.removeUserData
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.python.pyproject.model.api.ModuleCreateInfo
import com.intellij.python.pyproject.model.api.SuggestedSdk
import com.intellij.python.pyproject.model.api.getModuleInfo
import com.intellij.python.pyproject.model.api.suggestSdk
import com.intellij.python.sdkConfigurator.backend.impl.ModulesSdkConfigurator.Companion.create
import com.intellij.python.sdkConfigurator.backend.impl.ModulesSdkConfigurator.Companion.popModulesSDKConfigurator
import com.intellij.python.sdkConfigurator.common.impl.ModuleDTO
import com.intellij.python.sdkConfigurator.common.impl.ModuleName
import com.jetbrains.python.PathShortener
import com.jetbrains.python.Result
import com.jetbrains.python.module.PyModuleService
import com.jetbrains.python.sdk.configuration.CreateSdkInfo
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfigurationExtension
import com.jetbrains.python.sdk.configuration.createSdk
import com.jetbrains.python.sdk.configuration.getSdkCreator
import com.jetbrains.python.sdk.getOrCreateAdditionalData
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import com.jetbrains.python.sdk.pythonSdk
import com.jetbrains.python.sdk.setAssociationToPath
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

/**
 * Configures SDK for modules in [project] in [ModuleConfigurationMode.INTERACTIVE] mode.
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
  private val pathShorter: PathShortener,
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
          children.getOrPut(createInfo.parentModule.name) { HashSet() }.add(moduleName)
        }
      }
    }
    //Map modules
    modulesDTO = modules.mapNotNull { (moduleName, createInfo) ->
      when (createInfo) {
        is ModuleCreateInfo.CreateSdkInfoWrapper -> {
          val version = when (val r = createInfo.createSdkInfo) {
            is CreateSdkInfo.ExistingEnv -> r.pythonInfo.languageLevel.toPythonVersion()
            is CreateSdkInfo.WillCreateEnv, is CreateSdkInfo.WillInstallTool -> null
          }
          ModuleDTO(moduleName,
                    path = createInfo.moduleDir?.let { pathShorter.toString(it) },
                    createdByTool = createInfo.toolId.id,
                    existingPyVersion = version,
                    childModules = children[moduleName]!!.toList().sorted().toPersistentList())
        }
        is ModuleCreateInfo.SameAs -> null
      }
    }
      .sortedBy { it.name }
      .toList()
  }

  internal companion object {

    /**
     * Create instance and save in [project], see class doc
     */
    suspend fun create(project: Project): ModulesSdkConfigurator =
      ModulesSdkConfigurator(project, getModulesWithoutSDKCreateInfo(project), PathShortener.create(project)).also {
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

    private suspend fun getModulesWithoutSDKCreateInfo(project: Project): Map<ModuleName, ModuleCreateInfo> =
      withBackgroundProgress(project, PySdkConfiguratorBundle.message("intellij.python.sdk.looking")) {
        val tools = PyProjectSdkConfigurationExtension.createMap()
        val now = System.currentTimeMillis()
        val resultDef = project.modules.filter { PythonSdkUtil.findPythonSdk(it) == null }.map { module ->
          async {
            val moduleInfo = module.getModuleInfo(tools) ?: return@async null
            Pair(module, moduleInfo)
          }
        }
        val result = resultDef.awaitAll().filterNotNull()
        logger.debug { "SDKs calculated in ${System.currentTimeMillis() - now}ms" }
        result.associate { (module, createInfoAndDTO) ->
          Pair(module.name, createInfoAndDTO)
        }
      }


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
              when (val r = createInfo.createSdkInfo.createSdk(module)) {
                is Result.Failure -> { //TODO: Show SDK creation error?
                  logger.warn("Failed to create SDK for ${module.name}: ${r.error}")
                }
                is Result.Success -> {
                  val sdk = r.result
                  module.pythonSdk = sdk
                }
              }
            }
            is ModuleCreateInfo.SameAs -> {
              val parentModuleName = createInfo.parentModule.name
              val parent = modulesMap[parentModuleName] ?: error("No parent module named $parentModuleName")
              modulesWithSameSdk[module] = parent
            }
          }
        } // Link workspace members with their workspace
        val reportedBrokenModules = mutableSetOf<Module>()
        for ((module, parentModule) in modulesWithSameSdk) {
          val parentSdk = PythonSdkUtil.findPythonSdk(module)
          if (parentSdk != null) {
            module.pythonSdk = parentSdk // This SDK is shared, no need to associate it
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

/**
 * See [ModuleConfigurationMode.AUTOMATIC]
 */
@ApiStatus.Internal // Opened for tests only: we can't put tests here because configurators are in communuty.impl
suspend fun configureSdkAutomatically(project: Project): Unit = withContext(Dispatchers.Default) {
  val moduleService = PyModuleService.getInstance()
  val modules = project.modules.filter { moduleService.isPythonModule(it) }

  when (modules.size) {
    0 -> return@withContext
    1 -> { // Single-module project, associate first module only
      val module = modules.first()
      if (PythonSdkUtil.findPythonSdk(module) != null) return@withContext // Already has SDK
      configureSdkForModuleAutomatically(module, createEnvIfNeeded = false)
    }
    else -> { // Multi-module project, only associate workspace members
      val modulesToSdk = modules.filter { PythonSdkUtil.findPythonSdk(it) == null }.associateWith { it.suggestSdk() }
      for ((module, sdkSuggestion) in modulesToSdk) {
        when (sdkSuggestion) {
          is SuggestedSdk.PyProjectIndependent, null -> {
            logger.trace { "${module.name} skipped in multimodule project autoconfig" }
          }
          is SuggestedSdk.SameAs -> {
            setModuleSdkAsParent(parentModule = sdkSuggestion.parentModule, module = module)
          }
        }
      }
    }
  }
}

private suspend fun configureSdkForModuleAutomatically(module: Module, createEnvIfNeeded: Boolean) {
  when (val moduleInfo = module.getModuleInfo()) {
    is ModuleCreateInfo.CreateSdkInfoWrapper -> {
      when (val info = moduleInfo.createSdkInfo) {
        is CreateSdkInfo.ExistingEnv -> {
          info.createAndSetToModule(module)
        }
        is CreateSdkInfo.WillCreateEnv -> {
          if (createEnvIfNeeded) {
            info.createAndSetToModule(module)
          }
          else {
            logger.trace { "${module.name} can't be configured automatically: no venv for ${moduleInfo.toolId}" }
          }
        }
        is CreateSdkInfo.WillInstallTool -> logger.trace { "${module.name} can't be configured automatically: no tool installed - ${moduleInfo.toolId}" }
      }
    }
    is ModuleCreateInfo.SameAs -> {
      val parentModule = moduleInfo.parentModule
      setModuleSdkAsParent(parentModule = parentModule, module = module)
    }
    null -> Unit
  }
}

private suspend fun CreateSdkInfo.createAndSetToModule(module: Module) {
  when (val r = getSdkCreator(module).createSdk()) {
    is Result.Failure -> {
      logger.trace { "Failed to create sdk for ${module.name} : ${r.error}" }
    }
    is Result.Success -> {
      val sdk = r.result
      module.pythonSdk = sdk
      logger.trace { "SDK creation result for  ${module.name} : $sdk" }
    }
  }
}

private fun setModuleSdkAsParent(
  parentModule: Module,
  module: Module,
) {
  val parentSdk = PythonSdkUtil.findPythonSdk(parentModule) ?: return
  logger.trace { "${module.name} seeds the same sdk as ${parentModule} : ${parentSdk}" }
  module.pythonSdk = parentSdk
}


private val logger = fileLogger()
