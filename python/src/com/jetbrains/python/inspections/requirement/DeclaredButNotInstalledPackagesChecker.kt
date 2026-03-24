// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.requirement

import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VfsUtil
import com.jetbrains.python.packaging.PyPackageName
import com.jetbrains.python.packaging.PyRequirement
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.toRequirements
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.extractDependenciesAsync
import com.jetbrains.python.psi.PyUtil

class DeclaredButNotInstalledPackagesChecker(
  ignoredPackages: Collection<String>,
) {
  private val ignoredPackageNames: Set<String> = ignoredPackages.mapTo(mutableSetOf()) { PyPackageName.normalizePackageName(it) }

  fun findUnsatisfiedRequirements(module: Module, manager: PythonPackageManager): List<PyRequirement> {
    val requirements = manager.extractDependenciesAsync() ?: return emptyList()
    val packagesToCheck = filterToMainPackages(requirements, manager)
    val installedPackages = manager.listInstalledPackagesSnapshot()
    val modulePackages = collectPackagesInModule(module)

    return packagesToCheck.toRequirements().filter { requirement ->
      isRequirementUnsatisfied(requirement, installedPackages, modulePackages)
    }
  }

  private fun filterToMainPackages(packages: List<PythonPackage>, manager: PythonPackageManager): List<PythonPackage> {
    if (!manager.installedMightBeTransitive) return packages
    return packages.filter { it.dependencyGroup == null }
  }

  private fun isRequirementUnsatisfied(
    requirement: PyRequirement,
    installedPackages: List<PythonPackage>,
    modulePackages: List<PythonPackage>,
  ): Boolean {
    if (requirement.name in ignoredPackageNames) {
      return false
    }

    return !(installedPackages + modulePackages).any { it.matches(requirement) }
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