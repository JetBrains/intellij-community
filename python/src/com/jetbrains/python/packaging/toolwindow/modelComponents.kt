// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging.toolwindow

import com.intellij.openapi.util.NlsSafe
import com.jetbrains.python.packaging.repository.PyPackageRepository
import com.jetbrains.python.packaging.common.PythonPackage

sealed class DisplayablePackage(@NlsSafe val name: String, val repository: PyPackageRepository)
class InstalledPackage(val instance: PythonPackage, repository: PyPackageRepository) : DisplayablePackage(instance.name, repository)
class InstallablePackage(name: String, repository: PyPackageRepository) : DisplayablePackage(name, repository)
class ExpandResultNode(var more: Int, repository: PyPackageRepository) : DisplayablePackage("", repository)
open class PyPackagesViewData(@NlsSafe val repository: PyPackageRepository, val packages: List<DisplayablePackage>, val exactMatch: Int = -1, val moreItems: Int = 0)
class PyInvalidRepositoryViewData(repository: PyPackageRepository) : PyPackagesViewData(repository, emptyList())