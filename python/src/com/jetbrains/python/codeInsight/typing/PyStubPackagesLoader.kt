// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.typing

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.util.CatchingConsumer
import com.intellij.webcore.packaging.PackageManagementService
import com.intellij.webcore.packaging.RepoPackage
import com.jetbrains.python.packaging.PyPIPackageUtil
import com.jetbrains.python.packaging.PyPackage
import java.util.*
import java.util.function.BiConsumer


fun loadStubPackagesForSources(sourcesToLoad: Set<String>,
                               sourceToPackage: Map<String, String>,
                               installedPackages: List<PyPackage>,
                               availablePackages: List<RepoPackage>,
                               packageManagementService: PackageManagementService,
                               sdk: Sdk) {
  val sourceToStubPackagesAvailableToInstall = sourceToStubPackagesAvailableToInstall(
    sourceToInstalledRuntimeAndStubPackages(sourcesToLoad, sourceToPackage, installedPackages),
    availablePackages
  )

  loadRequirementsAndExtraArgs(
    sourceToStubPackagesAvailableToInstall,
    packageManagementService,
    BiConsumer { source, stubPackagesForSource ->
      ApplicationManager.getApplication().getService(PyStubPackagesAdvertiserCache::class.java).forSdk(sdk).put(source, stubPackagesForSource)
    }
  )
}

private fun sourceToInstalledRuntimeAndStubPackages(sourcesToLoad: Set<String>,
                                                    sourceToPackage: Map<String, String>,
                                                    installedPackages: List<PyPackage>): Map<String, List<Pair<PyPackage, PyPackage?>>> {
  val result = mutableMapOf<String, List<Pair<PyPackage, PyPackage?>>>()

  for (source in sourcesToLoad) {
    val pkgName = sourceToPackage[source] ?: continue
    installedRuntimeAndStubPackages(pkgName, installedPackages)?.let { result.put(source, listOf(it)) }
  }

  return result
}

private fun sourceToStubPackagesAvailableToInstall(sourceToInstalledRuntimeAndStubPkgs: Map<String, List<Pair<PyPackage, PyPackage?>>>,
                                                   availablePackages: List<RepoPackage>): Map<String, Set<RepoPackage>> {
  if (sourceToInstalledRuntimeAndStubPkgs.isEmpty()) return emptyMap()

  val stubPkgsAvailableToInstall = mutableMapOf<String, RepoPackage>()
  availablePackages.forEach { if (it.name.endsWith(STUBS_SUFFIX)) stubPkgsAvailableToInstall[it.name] = it }

  val result = mutableMapOf<String, Set<RepoPackage>>()
  sourceToInstalledRuntimeAndStubPkgs.forEach { (source, runtimeAndStubPkgs) ->
    result[source] = runtimeAndStubPkgs
      .asSequence()
      .filter { it.second == null }
      .mapNotNull { stubPkgsAvailableToInstall["${it.first.name}$STUBS_SUFFIX"] }
      .toSet()
  }

  return result
}

private fun loadRequirementsAndExtraArgs(sourceToStubPackagesAvailableToInstall: Map<String, Set<RepoPackage>>,
                                         packageManagementService: PackageManagementService,
                                         consumer: BiConsumer<String, PyStubPackagesAdvertiserCache.Companion.StubPackagesForSource>) {
  val commonState = CommonState(packageManagementService, consumer)

  for ((source, stubPackages) in sourceToStubPackagesAvailableToInstall) {
    if (stubPackages.isNotEmpty()) {
      val queue = LinkedList(stubPackages)
      loadRequirementAndExtraArgsForPackageAndThenContinueForSource(
        SourcePackageState(source, queue.poll(), queue, mutableMapOf(), commonState)
      )
    }
  }
}

private fun installedRuntimeAndStubPackages(pkgName: String, installedPackages: List<PyPackage>): Pair<PyPackage, PyPackage?>? {
  var runtime: PyPackage? = null
  var stub: PyPackage? = null
  val stubPkgName = "$pkgName$STUBS_SUFFIX"

  for (pkg in installedPackages) {
    val name = pkg.name

    if (name == pkgName) runtime = pkg
    if (name == stubPkgName) stub = pkg
  }

  return if (runtime == null) null else runtime to stub
}

private fun loadRequirementAndExtraArgsForPackageAndThenContinueForSource(state: SourcePackageState) {
  val commonState = state.commonState
  val name = state.pkg.name

  commonState.packageManagementService.fetchPackageVersions(
    name,
    object : CatchingConsumer<List<String>, Exception> {
      override fun consume(e: Exception?) = continueLoadingRequirementsAndExtraArgsForSource(state)

      override fun consume(t: List<String>?) {
        if (!t.isNullOrEmpty()) {
          val url = state.pkg.repoUrl
          val extraArgs =
            if (!url.isNullOrBlank() && !PyPIPackageUtil.isPyPIRepository(url)) {
              listOf("--extra-index-url", url)
            }
            else {
              emptyList()
            }

          state.result[name] = t.first() to extraArgs
        }

        continueLoadingRequirementsAndExtraArgsForSource(state)
      }
    }
  )
}

private fun continueLoadingRequirementsAndExtraArgsForSource(state: SourcePackageState) {
  val nextState = state.moveToNextPackage()
  if (nextState != null) {
    loadRequirementAndExtraArgsForPackageAndThenContinueForSource(nextState)
  }
  else {
    state.commonState.sourceResultConsumer.accept(
      state.source, PyStubPackagesAdvertiserCache.Companion.StubPackagesForSource.create(state.result)
    )
  }
}

private class SourcePackageState(
  val source: String,
  val pkg: RepoPackage,
  val queue: Queue<RepoPackage>,
  val result: MutableMap<String, Pair<String, List<String>>>,
  val commonState: CommonState
) {
  fun moveToNextPackage(): SourcePackageState? {
    return queue.poll()?.let { SourcePackageState(source, it, queue, result, commonState) }
  }
}

private class CommonState(
  val packageManagementService: PackageManagementService,
  val sourceResultConsumer: BiConsumer<String, PyStubPackagesAdvertiserCache.Companion.StubPackagesForSource>
)