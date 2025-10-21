// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.model

import com.intellij.openapi.util.NlsSafe
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.PyPackageVersion
import com.jetbrains.python.packaging.PyPackageVersionComparator
import com.jetbrains.python.packaging.PyPackageVersionNormalizer
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.repository.PyPackageRepository
import org.jetbrains.annotations.Nls
import javax.swing.Icon

sealed class DisplayablePackage(val name: @NlsSafe String, open val repository: PyPackageRepository?) {
  open fun getRequirements(): List<RequirementPackage> = emptyList()
}

class InstalledPackage(val instance: PythonPackage, repository: PyPackageRepository?, val nextVersion: PyPackageVersion? = null, private val requirements: List<RequirementPackage>) : DisplayablePackage(instance.name, repository) {
  val currentVersion: PyPackageVersion? = PyPackageVersionNormalizer.normalize(instance.version)

  val isEditMode: Boolean = instance.isEditableMode
  val sourceRepoIcon: Icon? = instance.sourceRepoIcon

  val canBeUpdated: Boolean
    get() {
      currentVersion ?: return false
      return nextVersion != null && PyPackageVersionComparator.compare(nextVersion, currentVersion) > 0
    }

  override fun getRequirements(): List<RequirementPackage> = requirements
}

class RequirementPackage(val instance: PythonPackage, override val repository: PyPackageRepository, private val requirements: List<RequirementPackage> = emptyList()) : DisplayablePackage(instance.name, repository) {
  val sourceRepoIcon: Icon? = instance.sourceRepoIcon

  override fun getRequirements(): List<RequirementPackage> = requirements
}

class InstallablePackage(name: String, override val repository: PyPackageRepository) : DisplayablePackage(name, repository)

class ExpandResultNode(var more: Int, override val repository: PyPackageRepository) : DisplayablePackage("", repository)

open class PyPackagesViewData(val repository: PyPackageRepository, val packages: List<DisplayablePackage>, val exactMatch: Int = -1, val moreItems: Int = 0)

class PyInvalidRepositoryViewData(repository: PyPackageRepository) : PyPackagesViewData(repository, emptyList())

data class PackageQuickFix(
  val name: @Nls String,
  val action: (suspend () -> PyResult<*>)
)

class ErrorNode(
  val description: @Nls String,
  val quickFix: PackageQuickFix
) : DisplayablePackage("", null)
