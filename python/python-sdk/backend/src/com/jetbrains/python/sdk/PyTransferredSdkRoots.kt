// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.google.common.collect.MultimapBuilder
import com.google.common.collect.SetMultimap
import com.intellij.ide.projectView.actions.MarkRootsManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.python.sdk.backend.detectedEnvironment
import com.intellij.python.sdk.backend.pythonInterpreterResult
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jps.model.java.JavaSourceRootType

/**
 * The paths of [sdk] inside the modules that use it, which become source roots of those modules. A path of a module is
 * no class root of the SDK, else the module is a library, and the editor opens its files in reader mode. Only the legacy
 * flow stores them: the interpreter library flow derives the source roots of a module from its interpreter.
 */
@ApiStatus.Internal
fun getPathsToTransfer(sdk: Sdk): Set<VirtualFile> {
  return (sdk.sdkAdditionalData as? PythonSdkAdditionalData)?.pathsToTransfer ?: emptySet()
}

/**
 * Turns [getPathsToTransfer] result into [module] source roots and dependencies. [sdk] is the interpreter that the caller
 * gives [module], so a caller passes it right after the set, before a snapshot states it.
 */
@ApiStatus.Internal
fun transferRoots(module: Module, sdk: Sdk?) {
  if (sdk != null) {
    runInEdt {
      val transferredRoots = getPathsToTransfer(sdk)
      val newTransferredRoots = TransferredRootsDetector(module.project).detect(module, transferredRoots, transferredRoots)
      addTransferredRoots(module, newTransferredRoots)
    }
  }
}

/**
 * Removes [getPathsToTransfer] result from [module] source roots and dependencies. [sdk] is the interpreter that
 * [module] had until the caller changed it.
 */
@ApiStatus.Internal
fun removeTransferredRoots(module: Module, sdk: Sdk?) {
  if (sdk != null) {
    runInEdt {
      val transferredRoots = getPathsToTransfer(sdk)
      val newTransferredRoots = TransferredRootsDetector(module.project).detect(module, transferredRoots, transferredRoots)
      removeTransferredRoots(module, newTransferredRoots)
    }
  }
}

/**
 * Moves the transferred roots of [sdk] in the modules of [project] whose SDK is [sdk], from [oldPaths] to [newPaths]. The
 * caller has written [newPaths] as the paths to transfer of [sdk]. A module whose SDK is not [sdk] yet gets them when it
 * gets [sdk], see [PySdkTransferredRootsListener].
 */
internal fun updateTransferredRoots(project: Project, sdk: Sdk, oldPaths: Set<VirtualFile>, newPaths: Set<VirtualFile>) {
  runInEdt {
    if (project.isDisposed) return@runInEdt
    val detector = TransferredRootsDetector(project)
    for (module in detector.projectModules.filter { ModuleRootManager.getInstance(it).sdk == sdk }) {
      removeTransferredRoots(module, detector.detect(module, oldPaths, oldPaths))
      addTransferredRoots(module, detector.detect(module, newPaths, oldPaths))
    }
  }
}

/** Excludes the environment of [sdk] from this module, when the environment is inside it. */
@ApiStatus.Internal
fun Module.excludeInnerVirtualEnv(sdk: Sdk) {
  val root = sdk.pythonInterpreterResult().successOrNull?.detectedEnvironment?.pythonHomePath?.let {
    VirtualFileManager.getInstance().findFileByNioPath(it)
  } ?: return

  runInEdt {
    MarkRootsManager.modifyRoots(this, arrayOf(root)) { vFile, entry ->
      entry.addExcludeFolder(vFile)
    }
  }
}

/**
 * Handles transferred roots and virtualenv exclusion when a module's Python SDK changes.
 * Registered as a listener for [PySdkListener.TOPIC].
 */
internal class PySdkTransferredRootsListener : PySdkListener {
  override fun moduleSdkUpdated(module: Module, prevSdk: Sdk?, newSdk: Sdk?) {
    if (prevSdk != null) {
      val oldPaths = getPathsToTransfer(prevSdk)
      if (oldPaths.isNotEmpty()) {
        runInEdt {
          val rootsToRemove = TransferredRootsDetector(module.project).detect(module, oldPaths, oldPaths)
          removeTransferredRoots(module, rootsToRemove)
        }
      }
    }

    if (newSdk != null) {
      transferRoots(module, newSdk)
      module.excludeInnerVirtualEnv(newSdk)
    }
  }
}

private val LOG = fileLogger()

private data class ModuleTransferredRoots(val sourceRoots: Set<VirtualFile>, val dependencies: Set<Module>)

@RequiresEdt
private fun addTransferredRoots(module: Module, roots: ModuleTransferredRoots) {
  if (roots.sourceRoots.isEmpty() && roots.dependencies.isEmpty()) return
  LOG.info("Adding source roots ${roots.sourceRoots} and dependencies ${roots.dependencies} to module $module")
  ApplicationManager.getApplication().runWriteAction {
    val model = ModuleRootManager.getInstance(module).modifiableModel
    for (root in roots.sourceRoots) {
      for (entry in model.contentEntries) {
        val file = entry.file
        if (file != null && VfsUtilCore.isAncestor(file, root, true)) {
          entry.addSourceFolder(root.url, JavaSourceRootType.SOURCE, true)
        }
      }
    }
    for (dependency in roots.dependencies) {
      if (dependency != module && model.findModuleOrderEntry(dependency) == null) model.addModuleOrderEntry(dependency)
    }
    model.commit()
  }
}

@RequiresEdt
private fun removeTransferredRoots(module: Module, roots: ModuleTransferredRoots) {
  if (roots.sourceRoots.isEmpty() && roots.dependencies.isEmpty()) return
  LOG.info("Removing source roots ${roots.sourceRoots} and dependencies ${roots.dependencies} from module $module")
  ApplicationManager.getApplication().runWriteAction {
    val model = ModuleRootManager.getInstance(module).modifiableModel
    for (entry in model.contentEntries) {
      for (folder in entry.sourceFolders) {
        if (folder.file in roots.sourceRoots) entry.removeSourceFolder(folder)
      }
    }
    for (dependency in roots.dependencies) {
      model.findModuleOrderEntry(dependency)?.let(model::removeOrderEntry)
    }
    model.commit()
  }
}

private class TransferredRootsDetector(project: Project) {
  val projectModules: List<Module> = runReadAction { ModuleManager.getInstance(project).modules }.toList()
  val moduleToContentRoots: SetMultimap<Module, VirtualFile> = MultimapBuilder.hashKeys().hashSetValues().build()
  val moduleToSourceRoots: SetMultimap<Module, VirtualFile> = MultimapBuilder.hashKeys().hashSetValues().build()

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
