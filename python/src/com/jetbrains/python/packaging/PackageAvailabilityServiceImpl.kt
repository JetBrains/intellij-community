// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseListener
import com.intellij.python.sdk.backend.PythonSdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.removeUserData
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.QualifiedName
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.findPythonInterpreterIfReady
import com.jetbrains.python.configuration.findModuleInterpreterIfReady
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.psi.resolve.PackageAvailabilityService
import com.jetbrains.python.psi.resolve.PackageAvailabilitySpec
import com.jetbrains.python.psi.resolve.PyResolveCachesInvalidator
import com.jetbrains.python.psi.resolve.fromInterpreter
import com.jetbrains.python.psi.resolve.isPackageAvailableKey
import com.jetbrains.python.psi.resolve.resolveQualifiedName
import com.jetbrains.python.pyi.PyiFile
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

/**
 * Checks if any of the fully-qualified names can be resolved to a real .py file (not .pyi stub)
 * in the library paths of [interpreter].
 *
 * This function performs PSI resolution and checks that the resolved element:
 * 1. Is not a .pyi stub file
 * 2. Is located in a library directory
 *
 * @param project the project context
 * @param interpreter the interpreter to resolve against
 * @param fqns the fully-qualified names to resolve (e.g., "pytest.fixture")
 * @return true if any FQN resolves to a .py file in library, false otherwise
 */
@ApiStatus.Internal
fun canResolveFqn(project: Project, interpreter: PythonInterpreter, fqns: List<String>): Boolean {
  val context = fromInterpreter(project, interpreter).copyWithMembers()
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
        PythonSdkTable.pythonSdksOfEveryTier().forEach { sdk ->
          check(sdk is UserDataHolderBase)
          sdk.userMap.keys.filter{ it.isPackageAvailableKey() }.forEach {
            sdk.removeUserData(it)
          }
        }
      }
    })
  }

  override fun isPackageAvailable(module: Module, spec: PackageAvailabilitySpec): Boolean {
    val interpreter = findModuleInterpreterIfReady(module) ?: return false

    // 1. First check package manager (has its own snapshot cache)
    if (isPackageInstalledInPackageManager(module.project, interpreter, spec.packageName)) {
      return true
    }

    // 2. Fallback to cached PSI resolution
    return canResolveFqnCached(module, spec)
  }

  override fun isPackageAvailable(project: Project, sdk: Sdk, spec: PackageAvailabilitySpec): Boolean {
    // Sync, so it does not wait. An SDK that this project holds no interpreter for has no packages to look at.
    val interpreter = project.findPythonInterpreterIfReady(sdk) ?: return false
    // 1. First check package manager (has its own snapshot cache)
    if (isPackageInstalledInPackageManager(project, interpreter, spec.packageName)) {
      return true
    }

    // 2. Fallback to cached PSI resolution
    return canResolveFqnCached(project, sdk, interpreter, spec)
  }

  override fun installedPackageVersion(project: Project, interpreter: PythonInterpreter, packageName: String): String? {
    val normalizedName = PyPackageName.from(packageName)
    return PythonPackageManager.forPythonInterpreter(project, interpreter).listInstalledPackagesSnapshot()
      .firstOrNull { it.normalizedName == normalizedName }?.version
  }

  private fun isPackageInstalledInPackageManager(project: Project, interpreter: PythonInterpreter, packageName: String): Boolean {
    val packageManager = PythonPackageManager.forPythonInterpreter(project, interpreter)
    val normalizedName = PyPackageName.from(packageName)
    return packageManager.listInstalledPackagesSnapshot()
      .any { it.normalizedName == normalizedName }
  }

  private fun canResolveFqnCached(module: Module, spec: PackageAvailabilitySpec): Boolean {
    return CachedValuesManager.getManager(module.project).getCachedValue(module, spec.cacheKey, {
      val interpreter = findModuleInterpreterIfReady(module)
      CachedValueProvider.Result.create(
        interpreter != null && canResolveFqn(module.project, interpreter, spec.fqns),
        VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS,
        // The interpreter comes from the snapshot, so a snapshot that changes an interpreter computes the value again.
        PyResolveCachesInvalidator.getInstance(module.project).modificationTracker,
      )
    }, false)
  }

  /** The value is stored on [sdk], see [PackageAvailabilityServiceImpl] that drops it when the project closes. */
  private fun canResolveFqnCached(project: Project, sdk: Sdk, interpreter: PythonInterpreter, spec: PackageAvailabilitySpec): Boolean {
    if (ApplicationManager.getApplication().isUnitTestMode) return canResolveFqn(project, interpreter, spec.fqns)
    return CachedValuesManager.getManager(project).getCachedValue(sdk, spec.cacheKey, {
      CachedValueProvider.Result.create(
        canResolveFqn(project, interpreter, spec.fqns),
        VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS
      )
    }, false)
  }
}
