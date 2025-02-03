@file:JvmName("IntellijDevLauncher")
package com.intellij.tools.devLauncher

import com.intellij.platform.runtime.loader.IntellijLoader
import com.intellij.platform.runtime.product.ProductMode
import com.intellij.platform.runtime.product.ProductModules
import com.intellij.platform.runtime.product.impl.ServiceModuleMapping
import com.intellij.platform.runtime.product.serialization.ProductModulesSerialization
import com.intellij.platform.runtime.product.serialization.ResourceFileResolver
import com.intellij.platform.runtime.repository.RuntimeModuleDescriptor
import com.intellij.platform.runtime.repository.RuntimeModuleId
import com.intellij.platform.runtime.repository.RuntimeModuleRepository
import org.jetbrains.annotations.Contract
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.inputStream
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
  val moduleRepositoryPath = Path.of(repositoryPathString).toAbsolutePath()
  val moduleRepository = RuntimeModuleRepository.create(moduleRepositoryPath)

  startupTimings.add("loading product modules")
  startupTimings.add(System.nanoTime())
  val projectHome = locateProjectHome(moduleRepositoryPath)
  val productModules = loadProductModules(moduleRepository, projectHome)

  startupTimings.add("building required modules")
  startupTimings.add(System.nanoTime())
  buildRequiredModules(productModules)

  IntellijLoader.launch(args, moduleRepository, startupTimings, startTimeUnixNano)
}

private fun loadProductModules(moduleRepository: RuntimeModuleRepository, projectHome: Path): ProductModules {
  val currentModeId = System.getProperty(PLATFORM_PRODUCT_MODE_PROPERTY, ProductMode.MONOLITH.id)
  val currentMode = ProductMode.entries.find { it.id == currentModeId }
  if (currentMode == null) {
    reportError("Unknown mode '$currentModeId' specified in '$PLATFORM_PRODUCT_MODE_PROPERTY' system property")
  }

  val resourceFileFinder = ModuleResourceFileFinder(projectHome)
  val resourceFileResolver = object : ResourceFileResolver {
    override fun readResourceFile(moduleId: RuntimeModuleId, relativePath: String): InputStream? {
      return resourceFileFinder.findResourceFile(moduleId.stringId, relativePath)?.inputStream()
    }

    override fun toString(): String {
      return "source file based resolver for '$projectHome' project"
    }
  }
  val productModulesPath = "META-INF/$rootModuleName/product-modules.xml"
  val productModulesXmlStream = resourceFileResolver.readResourceFile(RuntimeModuleId.module(rootModuleName), productModulesPath)
  if (productModulesXmlStream == null) {
    error("$productModulesPath is not found in '$rootModuleName' module")
  }
  return ProductModulesSerialization.loadProductModules(productModulesXmlStream, productModulesPath, currentMode, moduleRepository, resourceFileResolver)
}

private fun locateProjectHome(moduleRepositoryPath: Path): Path {
  val explicitHomePath = System.getProperty("idea.home.path")
  if (explicitHomePath != null) {
    return Path(explicitHomePath)
  }

  var currentPath: Path? = moduleRepositoryPath
  while (currentPath != null && !isProjectHome(currentPath)) {
    currentPath = currentPath.parent
  }
  require(currentPath != null) { "Cannot find project home directory for $moduleRepositoryPath" }
  return currentPath.toAbsolutePath()
}

private fun isProjectHome(path: Path): Boolean {
  return Files.isDirectory(path.resolve(".idea")) &&
         ((Files.exists(path.resolve("intellij.idea.ultimate.main.iml"))
           || Files.exists(path.resolve("intellij.idea.community.main.iml"))
           || Files.exists(path.resolve(".ultimate.root.marker"))))
}

private fun buildRequiredModules(productModules: ProductModules) {
  val allProductModuleDescriptors = LinkedHashSet<RuntimeModuleDescriptor>()
  productModules.mainModuleGroup.includedModules.mapTo(allProductModuleDescriptors) { it.moduleDescriptor }
  val serviceModuleMapping = ServiceModuleMapping.buildMapping(productModules)
  for (pluginModuleGroup in productModules.bundledPluginModuleGroups) {
    pluginModuleGroup.includedModules.mapTo(allProductModuleDescriptors) { it.moduleDescriptor }
    allProductModuleDescriptors.addAll(serviceModuleMapping.getAdditionalModules(pluginModuleGroup))
  }
  
  val moduleNames = mutableListOf(rootModuleName) 
  allProductModuleDescriptors.asSequence()
    .map { it.moduleId.stringId }
    .filterNotTo(moduleNames) { it.startsWith(RuntimeModuleId.LIB_NAME_PREFIX) }
  if (!sendRequestToCompileModules(moduleNames)) {
    reportError("Failed to build modules")
  }
}

@Contract("_ -> fail")
private fun reportError(message: String): Nothing {
  System.err.println(message)
  exitProcess(3) //com.intellij.idea.AppExitCodes.STARTUP_EXCEPTION
}

private val rootModuleName: String
  get() = System.getProperty(PLATFORM_ROOT_MODULE_PROPERTY) ?: error("'$PLATFORM_ROOT_MODULE_PROPERTY' system property is not specified")
private const val PLATFORM_ROOT_MODULE_PROPERTY = "intellij.platform.root.module"
private const val PLATFORM_PRODUCT_MODE_PROPERTY = "intellij.platform.product.mode"