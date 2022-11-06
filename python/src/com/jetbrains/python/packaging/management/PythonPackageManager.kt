// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.management

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.util.messages.Topic
import com.jetbrains.python.packaging.common.PackageManagerHolder
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonPackageManagementListener
import com.jetbrains.python.packaging.common.PythonPackageSpecification
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
abstract class PythonPackageManager(val project: Project, val sdk: Sdk) {
  abstract val installedPackages: List<PythonPackage>

  abstract val repositoryManager: PythonRepositoryManager

  abstract suspend fun installPackage(specification: PythonPackageSpecification)
  abstract suspend fun uninstallPackage(pkg: PythonPackage)

  abstract suspend fun reloadPackages()

  companion object {
    fun forSdk(project: Project, sdk: Sdk): PythonPackageManager? {
      return PackageManagerHolder.forSdk(project, sdk)
    }

    @Topic.AppLevel
    val PACKAGE_MANAGEMENT_TOPIC = Topic(PythonPackageManagementListener::class.java, Topic.BroadcastDirection.TO_DIRECT_CHILDREN)
  }
}