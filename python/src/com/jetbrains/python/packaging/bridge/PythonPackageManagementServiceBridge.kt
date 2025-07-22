// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.bridge

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.CatchingConsumer
import com.intellij.webcore.packaging.InstalledPackage
import com.intellij.webcore.packaging.PackageVersionComparator
import com.intellij.webcore.packaging.RepoPackage
import com.jetbrains.python.PyBundle
import com.jetbrains.python.getOrThrow
import com.jetbrains.python.isCondaVirtualEnv
import com.jetbrains.python.packaging.PyPackagingSettings
import com.jetbrains.python.packaging.common.PythonPackageDetails
import com.jetbrains.python.packaging.common.PythonRepositoryPackageSpecification
import com.jetbrains.python.packaging.common.PythonSimplePackageDetails
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.packagesByRepository
import com.jetbrains.python.packaging.management.ui.PythonPackageManagerUI
import com.jetbrains.python.packaging.management.ui.installPackageBackground
import com.jetbrains.python.packaging.pyRequirementVersionSpec
import com.jetbrains.python.packaging.repository.PyPIPackageRepository
import com.jetbrains.python.packaging.repository.PyPackageRepository
import com.jetbrains.python.packaging.requirement.PyRequirementRelation
import com.jetbrains.python.packaging.toolwindow.PyPackagingToolWindowService
import com.jetbrains.python.packaging.ui.PyPackageManagementService
import com.jetbrains.python.util.runWithModalBlockingOrInBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PythonPackageManagementServiceBridge(project: Project, sdk: Sdk) : PyPackageManagementService(project, sdk), Disposable {

  private val scope = project.service<PyPackagingToolWindowService>().serviceScope

  private val manager: PythonPackageManager
    get() = PythonPackageManager.forSdk(project, sdk)

  private val repositoryManager
    get() = manager.repositoryManager
  private val managerUI: PythonPackageManagerUI
    get() = PythonPackageManagerUI.forSdk(project, sdk)

  var useConda: Boolean = true
  val isConda: Boolean
    get() = sdk.isCondaVirtualEnv

  override fun getInstalledPackagesList(): List<InstalledPackage> {
    val packages = runWithModalBlockingOrInBackground(project, PyBundle.message("python.packaging.list.packages")) {
      manager.listInstalledPackages()
    }
    return packages.map { InstalledPackage(it.name, it.version) }
  }


  override fun getAllPackages(): List<RepoPackage> {
    val packagesWithRepositories = repositoryManager.packagesByRepository()
    return packagesWithRepositories
      .flatMap { (repository, packages) ->
        packages.asSequence().map { pkg ->
          createRepoPackage(pkg, repository)
        }
      }
      .toList()
  }

  private fun createRepoPackage(pkg: String, repository: PyPackageRepository): RepoPackage {
    val repositoryUrl = repository.repositoryUrl.takeIf { it != PyPIPackageRepository.repositoryUrl }
    return RepoPackage(pkg, repositoryUrl, null)
  }

  override fun getAllPackagesCached(): List<RepoPackage> {
    return allPackages
  }

  override fun reloadAllPackages(): List<RepoPackage> {
    return runBlockingCancellable {
      repositoryManager.refreshCaches()
      allPackages
    }
  }

  override fun installPackage(
    repoPackage: RepoPackage,
    version: String?,
    forceUpgrade: Boolean,
    extraOptions: String?,
    listener: Listener,
    installToUser: Boolean,
  ) {
    scope.launch(Dispatchers.IO + ModalityState.current().asContextElement()) {
      val versionSpec = version?.let { pyRequirementVersionSpec(PyRequirementRelation.EQ, version) }
      managerUI.installPackageBackground(repoPackage.name, versionSpec, listOfNotNull(extraOptions))
    }
  }


  override fun uninstallPackages(installedPackages: List<InstalledPackage>, listener: Listener) {
    scope.launch(Dispatchers.IO + ModalityState.current().asContextElement()) {
      managerUI.uninstallPackagesBackground(installedPackages.map<InstalledPackage, @NlsSafe String> { it.name })
    }
  }


  override fun fetchPackageVersions(packageName: String, consumer: CatchingConsumer<in List<String>, in Exception>) {
    scope.launch {
      val details = repositoryManager.getPackageDetails(specForPackage(packageName)).getOrThrow()
      consumer.consume(details.availableVersions.sortedWith(PackageVersionComparator.VERSION_COMPARATOR.reversed()))
    }
  }

  override fun fetchPackageDetails(packageName: String, consumer: CatchingConsumer<in String, in Exception>) {
    scope.launch {
      val details = repositoryManager.getPackageDetails(specForPackage(packageName)).getOrThrow()
      consumer.consume(buildDescription(details))
    }
  }

  override fun updatePackage(installedPackage: InstalledPackage, version: String?, listener: Listener) {
    installPackage(RepoPackage(installedPackage.name, null), version, true, null, listener, false)
  }

  override fun fetchLatestVersion(pkg: InstalledPackage, consumer: CatchingConsumer<in String, in Exception>) {
    scope.launch {
      val details = repositoryManager.getPackageDetails(specForPackage(pkg.name, pkg.version)).getOrThrow()
      consumer.consume(PyPackagingSettings.getInstance(project).selectLatestVersion(details.availableVersions))
    }
  }

  private fun buildDescription(details: PythonPackageDetails): String {
    return buildString {
      append(TEXT_PREFIX)
      with(details) {
        if (!summary.isNullOrBlank()) {
          append(summary).append("<br/>")
        }

        if (this is PythonSimplePackageDetails) {
          if (!author.isNullOrBlank()) {
            append("<h4>Author</h4>")
            append(author)
            append("<br/><br/>")
          }

          if (!authorEmail.isNullOrBlank()) {
            append("<br/>")
            append("<a href=\"mailto:$authorEmail\">mailto:$authorEmail<a/>")
          }

          if (!homepageUrl.isNullOrBlank()) {
            append("<br/>")
            append("<a href=\"$homepageUrl\">$homepageUrl<a/>")
          }
        }
      }
      append("</body></html>")
    }
  }

  private suspend fun specForPackage(packageName: String, version: String? = null, repository: PyPackageRepository? = null): PythonRepositoryPackageSpecification {
    return repositoryManager.findPackageSpecification(packageName, version = version, repository = repository)
           ?: throw IllegalArgumentException(PyBundle.message("python.packaging.error.package.is.not.listed.in.repositories", packageName))
  }

  override fun shouldFetchLatestVersionsForOnlyInstalledPackages(): Boolean = !(isConda && useConda)

  override fun dispose() {
    scope.cancel()
  }

}