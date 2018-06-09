// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging.pipenv

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName
import com.intellij.execution.ExecutionException
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.VfsUtil
import com.jetbrains.python.packaging.PyPackage
import com.jetbrains.python.packaging.PyPackageManager
import com.jetbrains.python.packaging.PyPackageUtil
import com.jetbrains.python.packaging.PyRequirement
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.python.sdk.pipenv.pipFileLockRequirements
import com.jetbrains.python.sdk.pipenv.runPipEnv
import com.jetbrains.python.sdk.pythonSdk

/**
 * @author vlan
 */
class PyPipEnvPackageManager(val sdk: Sdk) : PyPackageManager() {
  @Volatile
  private var packages: List<PyPackage>? = null

  override fun installManagement() {}

  override fun hasManagement() = true

  override fun install(requirementString: String) {
    install(parseRequirements(requirementString), emptyList())
  }

  override fun install(requirements: List<PyRequirement>?, extraArgs: List<String>) {
    val args = listOfNotNull(listOf("install"),
                             requirements?.flatMap { it.installOptions },
                             extraArgs)
      .flatten()
    try {
      runPipEnv(sdk, *args.toTypedArray())
    }
    finally {
      refreshAndGetPackages(true)
    }
  }

  override fun uninstall(packages: List<PyPackage>) {
    val args = listOf("uninstall") +
               packages.map { it.name }
    try {
      runPipEnv(sdk, *args.toTypedArray())
    }
    finally {
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
    throw ExecutionException("Creating virtual environments based on Pipenv environments is not supported")
  }

  override fun getPackages() = packages

  override fun refreshAndGetPackages(alwaysRefresh: Boolean): List<PyPackage> {
    if (alwaysRefresh || packages == null) {
      val output = try {
        runPipEnv(sdk, "graph", "--json")
      }
      catch (e: ExecutionException) {
        packages = emptyList()
        throw e
      }
      packages = parsePipEnvGraph(output)
    }
    return packages ?: emptyList()
  }

  override fun getRequirements(module: Module): List<PyRequirement>? =
    module.pythonSdk?.pipFileLockRequirements

  override fun parseRequirements(text: String): List<PyRequirement> =
    PyPackageUtil.fix(PyRequirement.fromText(text))

  override fun getDependents(pkg: PyPackage): Set<PyPackage> {
    // TODO: Parse the dependency information from `pipenv graph`
    return emptySet()
  }

  companion object {
    private data class GraphPackage(@SerializedName("key") var key: String,
                                    @SerializedName("package_name") var packageName: String,
                                    @SerializedName("installed_version") var installedVersion: String,
                                    @SerializedName("required_version") var requiredVersion: String?)

    private data class GraphEntry(@SerializedName("package") var pkg: GraphPackage,
                                  @SerializedName("dependencies") var dependencies: List<GraphPackage>)

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
        .filterNotNull()
        .map { PyPackage(it.pkg.packageName, it.pkg.installedVersion, null, emptyList()) }
        .toList()
    }
  }
}