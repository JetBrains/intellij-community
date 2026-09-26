// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseListener
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.removeUserData
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.psi.resolve.PackageAvailabilityService
import com.jetbrains.python.psi.resolve.PackageAvailabilitySpec
import com.jetbrains.python.psi.resolve.fromSdk
import com.jetbrains.python.psi.resolve.isPackageAvailableKey
import com.jetbrains.python.psi.resolve.resolveQualifiedName
import com.jetbrains.python.pyi.PyiFile
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

/**
 * Checks if any of the fully-qualified names can be resolved to a real .py file (not .pyi stub)
 * in the SDK's library paths.
 *
 * This function performs PSI resolution and checks that the resolved element:
 * 1. Is not a .pyi stub file
 * 2. Is located in a library directory
 *
 * @param project the project context
 * @param sdk the Python SDK to resolve against
 * @param fqns the fully-qualified names to resolve (e.g., "pytest.fixture")
 * @return true if any FQN resolves to a .py file in library, false otherwise
 */
@ApiStatus.Internal
fun canResolveFqn(project: Project, sdk: Sdk, fqns: List<String>): Boolean {
  val context = fromSdk(project, sdk).copyWithMembers()
  val fileIndex = ProjectFileIndex.getInstance(project)

  fun canResolveSingleFqn(fqn: String): Boolean {
    val qName = QualifiedName.fromDottedString(fqn)
    return resolveQualifiedName(qName, context).any { element ->
      val file = element.containingFile ?: return@any false
      val virtualFile = file.virtualFile ?: return@any false
      file !is PyiFile && fileIndex.isInLibrary(virtualFile)
    }
  }

  fun compute(): Boolean = fqns.any { canResolveSingleFqn(it) }
  return if (ApplicationManager.getApplication().isReadAccessAllowed) compute() else runReadAction(::compute)
}

/**
 * Implementation of [PackageAvailabilityService] that uses [PythonPackageManager]
 * for fast package lookups with PSI resolution fallback.
 */
internal class PackageAvailabilityServiceImpl(val cs: CoroutineScope) : PackageAvailabilityService {

  init {
    val messageBus = ApplicationManager.getApplication().messageBus
    messageBus.connect(cs).subscribe(ProjectCloseListener.TOPIC, object : ProjectCloseListener {
      override fun projectClosed(project: Project) {
        ProjectJdkTable.getInstance().allJdks.filter { PythonSdkUtil.isPythonSdk(it, true) }.forEach { sdk ->
          check(sdk is UserDataHolderBase)
          sdk.userMap.keys.filter{ it.isPackageAvailableKey() }.forEach {
            sdk.removeUserData(it)
          }
        }
      }
    })
  }

  override fun isPackageAvailable(module: Module, spec: PackageAvailabilitySpec): Boolean {
    val sdk = PythonSdkUtil.findPythonSdk(module) ?: return false

    // 1. First check package manager (has its own snapshot cache)
    if (isPackageInstalledInPackageManager(module.project, sdk, spec.packageName)) {
      return true
    }

    // 2. Fallback to cached PSI resolution
    return canResolveFqnCached(module, spec)
  }

  override fun isPackageAvailable(project: Project, sdk: Sdk, spec: PackageAvailabilitySpec): Boolean {
    // 1. First check package manager (has its own snapshot cache)
    if (isPackageInstalledInPackageManager(project, sdk, spec.packageName)) {
      return true
    }

    // 2. Fallback to cached PSI resolution
    return canResolveFqnCached(project, sdk, spec)
  }

  private fun isPackageInstalledInPackageManager(project: Project, sdk: Sdk, packageName: String): Boolean {
    val packageManager = PythonPackageManager.forSdk(project, sdk)
    val normalizedName = PyPackageName.from(packageName)
    return packageManager.listInstalledPackagesSnapshot()
      .any { it.normalizedName == normalizedName }
  }

  private fun canResolveFqnCached(module: Module, spec: PackageAvailabilitySpec): Boolean {
    return CachedValuesManager.getManager(module.project).getCachedValue(module, spec.cacheKey, {
      val sdk = PythonSdkUtil.findPythonSdk(module)
      CachedValueProvider.Result.create(
        sdk != null && canResolveFqn(module.project, sdk, spec.fqns),
        VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS
      )
    }, false)
  }

  private fun canResolveFqnCached(project: Project, sdk: Sdk, spec: PackageAvailabilitySpec): Boolean {
    if (ApplicationManager.getApplication().isUnitTestMode) return canResolveFqn(project, sdk, spec.fqns)
    return CachedValuesManager.getManager(project).getCachedValue(sdk, spec.cacheKey, {
      CachedValueProvider.Result.create(
        canResolveFqn(project, sdk, spec.fqns),
        VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS
      )
    }, false)
  }
}
