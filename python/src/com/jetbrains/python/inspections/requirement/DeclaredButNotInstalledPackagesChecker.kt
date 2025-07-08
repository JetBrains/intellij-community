// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.requirement

import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VfsUtil
import com.jetbrains.python.packaging.PyRequirement
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.psi.PyUtil

class DeclaredButNotInstalledPackagesChecker(
  val ignoredPackages: Collection<String>,
) {
  fun findUnsatisfiedRequirements(module: Module, manager: PythonPackageManager): List<PyRequirement> {
    val requirements = manager.getDependencyManager()?.getDependencies() ?: return emptyList()
    val installedPackages = manager.listInstalledPackagesSnapshot()
    val modulePackages = collectPackagesInModule(module)

    return requirements.filter { requirement ->
      isRequirementUnsatisfied(requirement, installedPackages, modulePackages)
    }
  }

  private fun isRequirementUnsatisfied(
    requirement: PyRequirement,
    installedPackages: List<PythonPackage>,
    modulePackages: List<PythonPackage>,
  ): Boolean {
    if (requirement.name in ignoredPackages) {
      return false
    }

    val isSatisfiedInInstalled = installedPackages.any { it.name == requirement.name }
    val isSatisfiedInModule = modulePackages.any { it.name == requirement.name }

    return !isSatisfiedInInstalled && !isSatisfiedInModule
  }

  private fun collectPackagesInModule(module: Module): List<PythonPackage> {
    return PyUtil.getSourceRoots(module).flatMap { srcRoot ->
      VfsUtil.getChildren(srcRoot).filter { file ->
        METADATA_EXTENSIONS.contains(file.extension)
      }.mapNotNull { metadataFile ->
        parsePackageNameAndVersion(metadataFile.nameWithoutExtension)
      }
    }
  }

  private fun parsePackageNameAndVersion(nameWithoutExtension: String): PythonPackage? {
    val components = splitNameIntoComponents(nameWithoutExtension)
    return if (components.size >= 2) PythonPackage(components[0], components[1], false) else null
  }


  companion object {
    private val METADATA_EXTENSIONS = setOf("egg-info", "dist-info")

    fun splitNameIntoComponents(name: String): Array<String> = name.split("-", limit = 3).toTypedArray()
  }
}