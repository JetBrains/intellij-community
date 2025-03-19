// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.poetry

import com.intellij.execution.ExecutionException
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.*
import com.jetbrains.python.packaging.common.PythonOutdatedPackage
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.python.sdk.associatedModuleDir


class PyPoetryPackageManager(sdk: Sdk) : PyPackageManager(sdk) {
  @Volatile
  private var packages: List<PyPackage>? = null

  private var requirements: List<PyRequirement>? = null

  private var outdatedPackages: Map<String, PythonOutdatedPackage> = emptyMap()

  override fun installManagement() {}

  override fun hasManagement() = true

  override fun install(requirementString: String) {
    install(parseRequirements(requirementString), emptyList())
  }

  override fun install(requirements: List<PyRequirement>?, extraArgs: List<String>) {
    val args = if (requirements.isNullOrEmpty()) {
      listOfNotNull(listOf("install"),
                    extraArgs)
        .flatten()
    }
    else {
      listOfNotNull(listOf("add"),
                    requirements.map { it.name },
                    extraArgs)
        .flatten()
    }

    try {
      runBlockingCancellable { runPoetryWithSdk(sdk, *args.toTypedArray()) }
    }
    finally {
      sdk.associatedModuleDir?.refresh(true, false)
      refreshAndGetPackages(true)
    }
  }

  override fun uninstall(packages: List<PyPackage>) {
    val args = listOf("remove") +
               packages.map { it.name }
    try {
      runBlockingCancellable { runPoetryWithSdk(sdk, *args.toTypedArray()) }
    }
    finally {
      sdk.associatedModuleDir?.refresh(true, false)
      refreshAndGetPackages(true)
    }
  }

  override fun refresh() {
    with(ApplicationManager.getApplication()) {
      invokeLater {
        runWriteAction {
          val files = sdk.rootProvider.getFiles(OrderRootType.CLASSES)
          VfsUtil.markDirtyAndRefresh(true, true, true, *files)
        }
        PythonSdkType.getInstance().setupSdkPaths(sdk)
      }
    }
  }

  override fun createVirtualEnv(destinationDir: String, useGlobalSite: Boolean): String {
    throw ExecutionException(
      PyBundle.message("python.sdk.dialog.message.creating.virtual.environments.based.on.poetry.environments.not.supported"))
  }

  override fun getPackages() = packages

  override fun refreshAndGetPackages(alwaysRefresh: Boolean): List<PyPackage> {
    if (alwaysRefresh || packages == null) {
      val allPackages = runBlockingCancellable {
        poetryListPackages(sdk)
      }.getOrElse {
        packages = emptyList()
        return emptyList()
      }

      packages = allPackages.first
      requirements = allPackages.second

      runBlockingCancellable {
        outdatedPackages = poetryShowOutdated(sdk).getOrElse {
          emptyMap()
        }
      }

      ApplicationManager.getApplication().messageBus.syncPublisher(PACKAGE_MANAGER_TOPIC).packagesRefreshed(sdk)
    }

    return packages ?: emptyList()
  }

  override fun getRequirements(module: Module): List<PyRequirement>? {
    return requirements
  }

  override fun parseRequirements(text: String): List<PyRequirement> =
    PyRequirementParser.fromText(text)

  override fun parseRequirement(line: String): PyRequirement? =
    PyRequirementParser.fromLine(line)

  override fun parseRequirements(file: VirtualFile): List<PyRequirement> =
    PyRequirementParser.fromFile(file)

  override fun getDependents(pkg: PyPackage): Set<PyPackage> {
    // TODO: Parse the dependency information from `pipenv graph`
    return emptySet()
  }
}