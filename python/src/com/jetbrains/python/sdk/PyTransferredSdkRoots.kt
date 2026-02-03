// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.google.common.collect.MultimapBuilder
import com.google.common.collect.SetMultimap
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.python.psi.PyUtil
import org.jetbrains.annotations.ApiStatus

/**
 * Applies [transferRoots] to all modules having [sdk] as a python sdk.
 */
@ApiStatus.Internal

fun transferRootsToModulesWithSdk(project: Project, sdk: Sdk) {
  updateRootsForModulesWithSdk(project, sdk, ::transferRoots)
}

/**
 * See [transferRootsToModulesWithSdk] and [removeTransferredRoots].
 */
@ApiStatus.Internal

fun removeTransferredRootsFromModulesWithSdk(project: Project, sdk: Sdk) {
  updateRootsForModulesWithSdk(project, sdk, ::removeTransferredRoots)
}

private fun updateRootsForModulesWithSdk(project: Project, sdk: Sdk?, action: (Module, Sdk) -> Unit) {
  if (sdk == null) {
    return
  }

  for (module in runReadAction { ModuleManager.getInstance(project).modules }) {
    action(module, sdk)
  }
}

/**
 * Applies [transferRoots] to all modules inheriting python sdk from the [project].
 */
@ApiStatus.Internal

fun transferRootsToModulesWithInheritedSdk(project: Project, sdk: Sdk?) {
  updateRootsForModulesWithInheritedSdk(project, sdk, ::transferRoots)
}

/**
 * See [transferRootsToModulesWithInheritedSdk] and [removeTransferredRoots].
 */
@ApiStatus.Internal

fun removeTransferredRootsFromModulesWithInheritedSdk(project: Project, sdk: Sdk?) {
  updateRootsForModulesWithInheritedSdk(project, sdk, ::removeTransferredRoots)
}

/**
 * Returns [sdk] paths that are located under project modules and hence should be turned into source roots,
 * at least to avoid enabling reader mode for them.
 */
@ApiStatus.Internal

fun getPathsToTransfer(sdk: Sdk): Set<VirtualFile> {
  return (sdk.sdkAdditionalData as? PythonSdkAdditionalData)?.pathsToTransfer ?: emptySet()
}

@ApiStatus.Internal

fun setPathsToTransfer(sdk: Sdk, roots: Set<VirtualFile>) {
  runInEdt {
    if (roots.isNotEmpty() || getPathsToTransfer(sdk).isNotEmpty()) { // do not create additional data with no reason
      sdk.getOrCreateAdditionalData()
      sdk.sdkModificator.apply {
        (sdkAdditionalData as PythonSdkAdditionalData).setPathsToTransferFromVirtualFiles(roots)
        runWriteAction { commitChanges() }
      }
    }
  }
}

/**
 * Turns [getPathsToTransfer] result into [module] source roots and dependencies if [module] python sdk is [sdk].
 */
@ApiStatus.Internal

fun transferRoots(module: Module, sdk: Sdk?) {
  if (sdk != null && module.pythonSdk == sdk) {
    runInEdt {
      val transferredRoots = getPathsToTransfer(sdk)
      val newTransferredRoots = TransferredRootsDetector(module.project).detect(module, transferredRoots, transferredRoots)
      addTransferredRoots(module, newTransferredRoots)
    }
  }
}

private fun addTransferredRoots(module: Module, newTransferredRoots: ModuleTransferredRoots) {
  ModuleRootAndDepOps.LOG.info("Adding source roots ${newTransferredRoots.sourceRoots} to module ${module}")
  PyUtil.addSourceRoots(module, newTransferredRoots.sourceRoots)
  ModuleRootAndDepOps.LOG.info("Adding dependencies ${newTransferredRoots.dependencies} to module ${module}")
  PyUtil.addModuleDependencies(module, newTransferredRoots.dependencies)
}

/**
 * Removes [getPathsToTransfer] result from [module] source roots and dependencies if [module] python sdk is [sdk].
 */
@ApiStatus.Internal

fun removeTransferredRoots(module: Module, sdk: Sdk?) {
  if (sdk != null && module.pythonSdk == sdk) {
    runInEdt {
      val transferredRoots = getPathsToTransfer(sdk)
      val newTransferredRoots = TransferredRootsDetector(module.project).detect(module, transferredRoots, transferredRoots)
      removeTransferredRoots(module, newTransferredRoots)
    }
  }
}

private fun removeTransferredRoots(module: Module, newTransferredRoots: ModuleTransferredRoots) {
  ModuleRootAndDepOps.LOG.info("Removing source roots ${newTransferredRoots.sourceRoots} from module ${module}")
  PyUtil.removeSourceRoots(module, newTransferredRoots.sourceRoots)
  ModuleRootAndDepOps.LOG.info("Removing dependencies ${newTransferredRoots.dependencies} from module ${module}")
  PyUtil.removeModuleDependencies(module, newTransferredRoots.dependencies)
}

@ApiStatus.Internal

fun updateTransferredRoots(project: Project, sdk: Sdk, newInProjectPaths: Set<VirtualFile>) {
  val rootsDetector = TransferredRootsDetector(project)
  val modulesWithThisSdk = rootsDetector.projectModules.filter { it.pythonSdk == sdk }
  val oldTransferredRoots = getPathsToTransfer(sdk)
  val oldTransferredRootsStructure = modulesWithThisSdk.associateWith { rootsDetector.detect(it, oldTransferredRoots, oldTransferredRoots) }
  val newTransferredRootsStructure = modulesWithThisSdk.associateWith { rootsDetector.detect(it, newInProjectPaths, oldTransferredRoots) }
  val newTransferredRoots = newTransferredRootsStructure.values
    .flatMap { it.sourceRoots + it.dependencies.mapNotNull { it.baseDir } }
    .toSet()

  if (oldTransferredRoots != newTransferredRoots) {
    for ((module, newModuleRoots) in newTransferredRootsStructure.entries) {
      runInEdt {
        val oldModuleRoots = oldTransferredRootsStructure[module]
        if (oldModuleRoots != null) {
          removeTransferredRoots(module, oldModuleRoots)
        }
        addTransferredRoots(module, newModuleRoots)
      }
    }
    setPathsToTransfer(sdk, newTransferredRoots)
  }
}

private fun updateRootsForModulesWithInheritedSdk(project: Project, sdk: Sdk?, action: (Module, Sdk) -> Unit) {
  if (sdk == null) {
    return
  }

  for (module in runReadAction { ModuleManager.getInstance(project).modules }) {
    if (!ModuleRootManager.getInstance(module).isSdkInherited) {
      continue
    }

    action(module, sdk)
  }
}

private object ModuleRootAndDepOps {
  val LOG = thisLogger()
}

private data class ModuleTransferredRoots(val sourceRoots: Set<VirtualFile>, val dependencies: Set<Module>)

private class TransferredRootsDetector(private val project: Project) {
  val projectModules: List<Module> = runReadAction { ModuleManager.getInstance(project).modules }.toList()
  val moduleToContentRoots: SetMultimap<Module, VirtualFile> = MultimapBuilder.hashKeys().hashSetValues().build<Module, VirtualFile>()
  val moduleToSourceRoots: SetMultimap<Module, VirtualFile> = MultimapBuilder.hashKeys().hashSetValues().build<Module, VirtualFile>()

  init {
    for (mod in projectModules) {
      val moduleRootManager = ModuleRootManager.getInstance(mod)
      moduleToContentRoots.putAll(mod, moduleRootManager.contentRoots.toList())
      moduleToSourceRoots.putAll(mod, moduleRootManager.sourceRoots.toList())
    }
  }

  fun detect(module: Module, newPaths: Set<VirtualFile>, oldPaths: Set<VirtualFile>): ModuleTransferredRoots {
    val sourceRoots = linkedSetOf<VirtualFile>()
    val dependencies = linkedSetOf<Module>()
    val manuallyMarkedModuleSourceRoots = moduleToSourceRoots[module] - oldPaths
    val moduleContentRoots = moduleToContentRoots[module]
    for (path in newPaths) {
      val moduleForPath = moduleToContentRoots.entries()
        .filter { VfsUtil.isAncestor(it.value, path, false) }
        // Select the closest content root containing the file in case of nested modules
        .maxByOrNull { it.value.path.length }
        ?.key
      if (moduleForPath == null) continue
      if (moduleForPath == module) {
        if (path !in moduleContentRoots && path !in manuallyMarkedModuleSourceRoots) {
          sourceRoots.add(path)
        }
      }
      else if (path == moduleForPath.baseDir) {
        if (Registry.`is`("python.detect.cross.module.dependencies")) {
          dependencies.add(moduleForPath)
        }
      }
    }
    return ModuleTransferredRoots(sourceRoots, dependencies)
  }
}