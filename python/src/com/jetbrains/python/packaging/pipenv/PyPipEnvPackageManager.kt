// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging.pipenv

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName
import com.intellij.execution.ExecutionException
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.PySdkBundle
import com.jetbrains.python.packaging.*
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.python.sdk.associatedModuleDir
import com.jetbrains.python.sdk.associatedModulePath
import com.jetbrains.python.sdk.pipenv.pipFileLockRequirements
import com.jetbrains.python.sdk.pipenv.runPipEnv
import com.jetbrains.python.sdk.pythonSdk
import java.nio.file.Path

class PyPipEnvPackageManager(sdk: Sdk) : PyPackageManager(sdk) {
  @Volatile
  private var packages: List<PyPackage>? = null

  override fun installManagement() {}

  override fun hasManagement() = true

  @RequiresBackgroundThread
  override fun install(requirementString: String) {
    install(parseRequirements(requirementString), emptyList())
  }

  @RequiresBackgroundThread
  override fun install(requirements: List<PyRequirement>?, extraArgs: List<String>) {
    val args = listOfNotNull(listOf("install"),
                             requirements?.flatMap { it.installOptions },
                             extraArgs)
      .flatten()
    try {
      runBlockingCancellable { runPipEnv(sdk.associatedModulePath?.let { Path.of(it) }, *args.toTypedArray()) }
    }
    finally {
      sdk.associatedModuleDir?.refresh(true, false)
      refreshAndGetPackages(true)
    }
  }

  @RequiresBackgroundThread
  override fun uninstall(packages: List<PyPackage>) {
    val args = listOf("uninstall") +
               packages.map { it.name }
    try {
      runBlockingCancellable { runPipEnv(sdk.associatedModulePath?.let { Path.of(it) }, *args.toTypedArray()) }
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
    throw ExecutionException(PySdkBundle.message("python.sdk.pipenv.creating.venv.not.supported"))
  }

  override fun getPackages() = packages

  @RequiresBackgroundThread
  override fun refreshAndGetPackages(alwaysRefresh: Boolean): List<PyPackage> {
    if (alwaysRefresh || packages == null) {
      packages = null
      val output = runBlockingCancellable {
        runPipEnv(sdk.associatedModulePath?.let { Path.of(it) }, "graph", "--json")
      }.getOrElse {
        packages = emptyList()
        throw it
      }
      packages = parsePipEnvGraph(output)
      ApplicationManager.getApplication().messageBus.syncPublisher(PyPackageManager.PACKAGE_MANAGER_TOPIC).packagesRefreshed(sdk)
    }
    return packages ?: emptyList()
  }

  @RequiresBackgroundThread
  override fun getRequirements(module: Module): List<PyRequirement>? =
    runBlockingCancellable { module.pythonSdk?.let { pipFileLockRequirements(it) } }

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

  companion object {
    private data class GraphPackage(
      @SerializedName("key") var key: String,
      @SerializedName("package_name") var packageName: String,
      @SerializedName("installed_version") var installedVersion: String,
      @SerializedName("required_version") var requiredVersion: String?,
    )

    private data class GraphEntry(
      @SerializedName("package") var pkg: GraphPackage,
      @SerializedName("dependencies") var dependencies: List<GraphPackage>,
    )

    /**
     * Parses the output of `pipenv graph --json` into a list of packages.
     */
    private fun parsePipEnvGraph(input: String): List<PyPackage> {
      val entries = try {
        Gson().fromJson(input, Array<GraphEntry>::class.java)
      }
      catch (e: JsonSyntaxException) {
        // TODO: Log errors
        return emptyList()
      }
      return entries
        .asSequence()
        .flatMap { sequenceOf(it.pkg) + it.dependencies.asSequence() }
        .map { PyPackage(it.packageName, it.installedVersion) }
        .distinct()
        .toList()
    }
  }
}