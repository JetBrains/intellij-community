// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.stubs.checkers

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.jetbrains.python.codeInsight.stubs.PyStubsSuggestions
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.getInstalledPackage
import com.jetbrains.python.packaging.pyRequirementVersionSpec

@Service(Service.Level.PROJECT)
internal class PyNotInstalledStubsChecker(project: Project) : PyStubsChecker(project) {
  override suspend fun detectSuggestedStubs(packageManager: PythonPackageManager): List<PyPackageStubLink> {
    return PyStubsSuggestions.SUPPORTED_STUBS.mapNotNull { (packageName, stub) ->
      val stubRequirement = stub.first
      val stubRelationToPackage = stub.second
      val installedPackage = packageManager.getInstalledPackage(packageName.name) ?: return@mapNotNull null
      val installedStubPackage = packageManager.getInstalledPackage(stubRequirement.name)
      if (installedStubPackage != null)
        return@mapNotNull null

      if (stubRelationToPackage == null)
        return@mapNotNull PyPackageStubLink(packageName, stubRequirement)

      val expectedVersionSpec = pyRequirementVersionSpec(stubRelationToPackage, installedPackage.version)
      val expectedRequirement = stubRequirement.withVersionSpecs(listOf(expectedVersionSpec))

      val isStubExists = packageManager.repositoryManager.matchRequirement(expectedRequirement)
      if (!isStubExists)
        return@mapNotNull null
      PyPackageStubLink(packageName, expectedRequirement)
    }
  }

  companion object {
    fun getInstance(project: Project): PyNotInstalledStubsChecker = project.getService(PyNotInstalledStubsChecker::class.java)
  }
}