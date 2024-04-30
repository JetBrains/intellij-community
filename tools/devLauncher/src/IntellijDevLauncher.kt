@file:JvmName("IntellijDevLauncher")
package com.intellij.tools.devLauncher

import com.intellij.platform.runtime.loader.IntellijLoader
import com.intellij.platform.runtime.product.ProductMode
import com.intellij.platform.runtime.product.ProductModules
import com.intellij.platform.runtime.product.impl.ServiceModuleMapping
import com.intellij.platform.runtime.product.serialization.ProductModulesSerialization
import com.intellij.platform.runtime.repository.RuntimeModuleDescriptor
import com.intellij.platform.runtime.repository.RuntimeModuleId
import com.intellij.platform.runtime.repository.RuntimeModuleRepository
import org.jetbrains.annotations.Contract
import java.nio.file.Path
import kotlin.system.exitProcess

/**
 * Launches IntelliJ-based product which uses module-based loader from source code.
 * 
 * The containing module of this class doesn't depend on all modules included in the product, so they may not be compiled before a run 
 * configuration starts. So this function determines the list of required modules and sends a request to the IDE instance to compile them,
 * and then launches [IntellijLoader].
 */
fun main(args: Array<String>) {
  val startTimeNano = System.nanoTime()
  val startTimeUnixNano = System.currentTimeMillis() * 1000000
  val startupTimings = ArrayList<Any>(16)
  startupTimings.add("startup begin")
  startupTimings.add(startTimeNano)
  
  val repositoryPathString = System.getProperty(IntellijLoader.RUNTIME_REPOSITORY_PATH_PROPERTY)
  if (repositoryPathString == null) {
    reportError("${IntellijLoader.RUNTIME_REPOSITORY_PATH_PROPERTY} is not specified")
  }

  startupTimings.add("loading runtime module repository")
  startupTimings.add(System.nanoTime())
  val moduleRepository = RuntimeModuleRepository.create(Path.of(repositoryPathString).toAbsolutePath())

  startupTimings.add("loading product modules")
  startupTimings.add(System.nanoTime())
  val productModules = loadProductModules(moduleRepository)

  startupTimings.add("building required modules")
  startupTimings.add(System.nanoTime())
  buildRequiredModules(productModules)

  IntellijLoader.launch(args, moduleRepository, startupTimings, startTimeUnixNano)
}

private fun loadProductModules(moduleRepository: RuntimeModuleRepository): ProductModules {
  val currentModeId = System.getProperty(PLATFORM_PRODUCT_MODE_PROPERTY, ProductMode.LOCAL_IDE.id)
  val currentMode = ProductMode.entries.find { it.id == currentModeId }
  if (currentMode == null) {
    reportError("Unknown mode '$currentModeId' specified in '$PLATFORM_PRODUCT_MODE_PROPERTY' system property")
  }

  val rootModuleName = System.getProperty(PLATFORM_ROOT_MODULE_PROPERTY)
  if (rootModuleName == null) {
    error("'$PLATFORM_ROOT_MODULE_PROPERTY' system property is not specified")
  }

  val rootModule = moduleRepository.getModule(RuntimeModuleId.module(rootModuleName))
  val productModulesPath = "META-INF/$rootModuleName/product-modules.xml"
  val productModulesXmlStream = rootModule.readFile(productModulesPath)
  if (productModulesXmlStream == null) {
    error("$productModulesPath is not found in '$rootModuleName' module")
  }
  return ProductModulesSerialization.loadProductModules(productModulesXmlStream, productModulesPath, currentMode, moduleRepository)
}

private fun buildRequiredModules(productModules: ProductModules) {
  val allProductModuleDescriptors = LinkedHashSet<RuntimeModuleDescriptor>()
  productModules.mainModuleGroup.includedModules.mapTo(allProductModuleDescriptors) { it.moduleDescriptor }
  val serviceModuleMapping = ServiceModuleMapping.buildMapping(productModules)
  for (pluginModuleGroup in productModules.bundledPluginModuleGroups) {
    pluginModuleGroup.includedModules.mapTo(allProductModuleDescriptors) { it.moduleDescriptor }
    allProductModuleDescriptors.addAll(serviceModuleMapping.getAdditionalModules(pluginModuleGroup))
  }
  
  val moduleNames = allProductModuleDescriptors.asSequence()
    .map { it.moduleId.stringId }
    .filterNot { it.startsWith(RuntimeModuleId.LIB_NAME_PREFIX) }
    .toList()
  if (!sendRequestToCompileModules(moduleNames)) {
    reportError("Failed to build modules")
  }
}

@Contract("_ -> fail")
private fun reportError(message: String): Nothing {
  System.err.println(message)
  exitProcess(3) //com.intellij.idea.AppExitCodes.STARTUP_EXCEPTION
}

private const val PLATFORM_ROOT_MODULE_PROPERTY = "intellij.platform.root.module"
private const val PLATFORM_PRODUCT_MODE_PROPERTY = "intellij.platform.product.mode"