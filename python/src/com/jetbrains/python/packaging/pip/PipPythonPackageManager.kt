// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.pip

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.management.runPackagingTool
import com.jetbrains.python.packaging.common.PythonPackage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
class PipPythonPackageManager(project: Project, sdk: Sdk) : PipBasedPackageManager(project, sdk) {
  override var installedPackages: List<PythonPackage> = emptyList()
    private set

  override val repositoryManager: PipRepositoryManager = PipRepositoryManager(project, sdk)

  override suspend fun reloadPackages() {
    withContext(Dispatchers.IO) {
      val output = runPackagingTool("list", emptyList(), PyBundle.message("python.packaging.list.progress"))

      val packages = output.lines().filter { it.isNotBlank() }.map {
        val line = it.split("\t")
        PythonPackage(line[0], line[1])
      }

      withContext(Dispatchers.Main) {
        installedPackages = packages
      }

      ApplicationManager.getApplication()
        .messageBus
        .syncPublisher(PACKAGE_MANAGEMENT_TOPIC)
        .packagesChanged(sdk)
    }
  }

}