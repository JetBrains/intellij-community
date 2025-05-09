// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.model

import com.intellij.openapi.util.NlsSafe
import com.jetbrains.python.icons.PythonIcons
import com.jetbrains.python.packaging.PyPackageVersion
import com.jetbrains.python.packaging.PyPackageVersionComparator
import com.jetbrains.python.packaging.PyPackageVersionNormalizer
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.conda.CondaPackage
import com.jetbrains.python.packaging.repository.PyPackageRepository
import com.jetbrains.python.psi.icons.PythonPsiApiIcons
import javax.swing.Icon

sealed class DisplayablePackage(val name: @NlsSafe String, open val repository: PyPackageRepository?)

class InstalledPackage(val instance: PythonPackage, repository: PyPackageRepository?, val nextVersion: PyPackageVersion? = null) : DisplayablePackage(instance.presentableName, repository) {
  val currentVersion: PyPackageVersion? = PyPackageVersionNormalizer.normalize(instance.version)

  val isEditMode: Boolean = instance.isEditableMode
  val sourceRepoIcon: Icon?
    get() {
      val condaPackage = instance as? CondaPackage ?: return null
      return if (condaPackage.installedWithPip) {
        PythonPsiApiIcons.Python
      }
      else {
        PythonIcons.Python.Anaconda
      }
    }

  val canBeUpdated: Boolean
    get() {
      currentVersion ?: return false
      return nextVersion != null && PyPackageVersionComparator.compare(nextVersion, currentVersion) > 0
    }

  fun withNextVersion(newVersion: PyPackageVersion?): InstalledPackage {
    return InstalledPackage(instance, repository, newVersion)
  }
}


class InstallablePackage(name: String, override val repository: PyPackageRepository) : DisplayablePackage(name, repository)

class ExpandResultNode(var more: Int, override val repository: PyPackageRepository) : DisplayablePackage("", repository)

open class PyPackagesViewData(@NlsSafe val repository: PyPackageRepository, val packages: List<DisplayablePackage>, val exactMatch: Int = -1, val moreItems: Int = 0)

class PyInvalidRepositoryViewData(repository: PyPackageRepository) : PyPackagesViewData(repository, emptyList())