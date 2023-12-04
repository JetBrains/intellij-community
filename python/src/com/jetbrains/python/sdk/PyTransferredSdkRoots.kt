// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.python.psi.PyUtil

/**
 * Applies [transferRoots] to all modules having [sdk] as a python sdk.
 */
fun transferRootsToModulesWithSdk(project: Project, sdk: Sdk) {
  updateRootsForModulesWithSdk(project, sdk, ::transferRoots)
}

/**
 * See [transferRootsToModulesWithSdk] and [removeTransferredRoots].
 */
fun removeTransferredRootsFromModulesWithSdk(project: Project, sdk: Sdk) {
  updateRootsForModulesWithSdk(project, sdk, ::removeTransferredRoots)
}

/**
 * Applies [transferRoots] to all modules inheriting python sdk from the [project].
 */
fun transferRootsToModulesWithInheritedSdk(project: Project, sdk: Sdk?) {
  updateRootsForModulesWithInheritedSdk(project, sdk, ::transferRoots)
}

/**
 * See [transferRootsToModulesWithInheritedSdk] and [removeTransferredRoots].
 */
fun removeTransferredRootsFromModulesWithInheritedSdk(project: Project, sdk: Sdk?) {
  updateRootsForModulesWithInheritedSdk(project, sdk, ::removeTransferredRoots)
}

/**
 * Returns [sdk] paths that are located under project modules and hence should be turned into source roots,
 * at least to avoid enabling reader mode for them.
 */
fun getPathsToTransfer(sdk: Sdk): Set<VirtualFile> {
  return (sdk.sdkAdditionalData as? PythonSdkAdditionalData)?.pathsToTransfer ?: emptySet()
}

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
 * Turns [getPathsToTransfer] result into [module] source roots if [module] python sdk is [sdk].
 */
fun transferRoots(module: Module, sdk: Sdk?) {
  if (sdk != null && module.pythonSdk == sdk) {
    runInEdt {
      PyUtil.addSourceRoots(module, getPathsToTransfer(sdk))
    }
  }
}

/**
 * Removes [getPathsToTransfer] result from [module] source roots if [module] python sdk is [sdk].
 */
fun removeTransferredRoots(module: Module, sdk: Sdk?) {
  if (sdk != null && module.pythonSdk == sdk) {
    runInEdt {
      PyUtil.removeSourceRoots(module, getPathsToTransfer(sdk))
    }
  }
}

private fun updateRootsForModulesWithSdk(project: Project, sdk: Sdk?, action: (Module, Sdk) -> Unit) {
  if (sdk == null) {
    return
  }

  for (module in runReadAction { ModuleManager.getInstance(project).modules }) {
    action(module, sdk)
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

