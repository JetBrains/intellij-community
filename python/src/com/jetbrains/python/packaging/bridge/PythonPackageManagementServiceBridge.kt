// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.bridge

import com.intellij.execution.ExecutionException
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.util.CatchingConsumer
import com.intellij.webcore.packaging.InstalledPackage
import com.intellij.webcore.packaging.PackageVersionComparator
import com.intellij.webcore.packaging.RepoPackage
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.PyPackagingSettings
import com.jetbrains.python.packaging.common.*
import com.jetbrains.python.packaging.conda.*
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.packagesByRepository
import com.jetbrains.python.packaging.management.runPackagingTool
import com.jetbrains.python.packaging.pip.PipPythonPackageManager
import com.jetbrains.python.packaging.repository.PyPIPackageRepository
import com.jetbrains.python.packaging.repository.PyPackageRepository
import com.jetbrains.python.packaging.ui.PyPackageManagementService
import kotlinx.coroutines.*

class PythonPackageManagementServiceBridge(project: Project,sdk: Sdk) : PyPackageManagementService(project, sdk), Disposable {

  private val scope = CoroutineScope(Dispatchers.IO)

  private val manager: PythonPackageManager
    get() = PythonPackageManager.forSdk(project, sdk)

  var useConda = true

  val isConda: Boolean
    get() = manager is CondaPackageManager


  override fun getInstalledPackagesList(): List<InstalledPackage> {
    if (manager.installedPackages.isEmpty()) runBlocking {
      manager.reloadPackages()
    }
    if (isConda) {
      if (useConda) {
       return manager.installedPackages.asSequence()
         .filterIsInstance<CondaPackage>()
         .filter { it.installedWithPip != useConda }
         .map { InstalledPackage(it.name, it.version) }
         .toList()
      }
      else {
        return runBlocking {
          val result = runPackagingOperationOrShowErrorDialog(sdk, PyBundle.message("python.packaging.operation.failed.title")) {
            val output = manager.runPackagingTool("list", emptyList(), PyBundle.message("python.packaging.list.progress"))

            val packages = output.lineSequence()
              .filter { it.isNotBlank() }
              .map {
                val line = it.split("\t")
                PythonPackage(line[0], line[1], isEditableMode = false)
              }
              .sortedWith(compareBy(PythonPackage::name))
              .toList()
            Result.success(packages)
          }
          return@runBlocking if (result.isSuccess)  {
             result.getOrThrow().map { InstalledPackage(it.name, it.version) }
          } else emptyList()
        }
      }
    }
    return manager.installedPackages.map { InstalledPackage(it.name, it.version) }
  }

  override fun getAllPackages(): List<RepoPackage> {
    if (isConda && useConda) {
      val settings = PyPackagingSettings.getInstance(project)
      val cache = service<CondaPackageCache>()
      return manager
        .repositoryManager
        .packagesFromRepository(CondaPackageRepository)
        .asSequence()
        .map { RepoPackage(it, null, settings.selectLatestVersion(cache[it] ?: emptyList())) }
        .toMutableList()
    }

    val hasRepositories = manager
      .repositoryManager
      .repositories
      .any { it !is PyPIPackageRepository && it !is CondaPackageRepository }

    return manager
      .repositoryManager
      .packagesByRepository()
      .filterNot { it.first is CondaPackageRepository }
      .flatMap { (repo, pkgs) -> pkgs.asSequence().map { RepoPackage(it, if (hasRepositories) repo.repositoryUrl else null) } }
      .toMutableList()
  }

  override fun getAllPackagesCached(): List<RepoPackage> {
    return allPackages
  }

  override fun reloadAllPackages(): List<RepoPackage> {
    return runBlocking {
      manager.repositoryManager.refreshCashes()
      allPackages
    }
  }

  override fun installPackage(repoPackage: RepoPackage,
                              version: String?,
                              forceUpgrade: Boolean,
                              extraOptions: String?,
                              listener: Listener,
                              installToUser: Boolean) {
    scope.launch(Dispatchers.IO + ModalityState.current().asContextElement()) {
      val repository = if (repoPackage.repoUrl != null) {
        manager.repositoryManager.repositories.find { it.repositoryUrl == repoPackage.repoUrl }
      } else null
      try {
        val specification = specForPackage(repoPackage.name, version, repository)
        runningUnderOldUI = true
        listener.operationStarted(specification.name)
        val result = manager.installPackage(specification, emptyList<String>())
        val exception = if (result.isFailure) mutableListOf(result.exceptionOrNull() as ExecutionException) else null
        listener.operationFinished(specification.name,
                                   toErrorDescription(exception, mySdk, specification.name))
      } finally {
        runningUnderOldUI = false
      }
    }
  }


  override fun uninstallPackages(installedPackages: List<InstalledPackage>, listener: Listener) {
    scope.launch(Dispatchers.IO + ModalityState.current().asContextElement()) {
      try {
        runningUnderOldUI = true
        val namesToDelete = installedPackages.map { it.name.lowercase() }
        manager
          .installedPackages
          .filter { it.name.lowercase() in namesToDelete }
          .forEach {
            runPackagingOperationOrShowErrorDialog(sdk, PyBundle.message("python.packaging.operation.failed.title")) {
              manager.uninstallPackage(it)
            }
          }

        listener.operationFinished(namesToDelete.first(), null)
      }
      finally {
        runningUnderOldUI = false
      }
    }
  }


  override fun fetchPackageVersions(packageName: String, consumer: CatchingConsumer<in List<String>, in Exception>) {
    scope.launch {
      val details = manager.repositoryManager.getPackageDetails(specForPackage(packageName))
      consumer.consume(details.availableVersions.sortedWith(PackageVersionComparator.VERSION_COMPARATOR.reversed()))
    }
  }

  override fun fetchPackageDetails(packageName: String, consumer: CatchingConsumer<in String, in Exception>) {
    scope.launch {
      val details = manager.repositoryManager.getPackageDetails(specForPackage(packageName))
      consumer.consume(buildDescription(details))
    }
  }

  override fun updatePackage(installedPackage: InstalledPackage, version: String?, listener: Listener) {
    installPackage(RepoPackage(installedPackage.name, null), version, true, null, listener, false)
  }

  override fun fetchLatestVersion(pkg: InstalledPackage, consumer: CatchingConsumer<in String, in Exception>) {
    scope.launch {
      val details = manager.repositoryManager.getPackageDetails(specForPackage(pkg.name, pkg.version))
      consumer.consume(PyPackagingSettings.getInstance(project).selectLatestVersion(details.availableVersions))
    }
  }

  private fun findRepositoryForPackage(name: String): PyPackageRepository {
    if (manager is CondaPackageManager && useConda) return CondaPackageRepository
    return manager
      .repositoryManager
      .packagesByRepository()
      .filterNot { it.first is CondaPackageRepository || it.first is PyPIPackageRepository }
      .firstOrNull { (_, packages) -> name in packages }
      ?.first ?: PyPIPackageRepository
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

  private fun specForPackage(packageName: String, version: String? = null, repository: PyPackageRepository? = null): PythonPackageSpecification {
    return when(manager) {
      is PipPythonPackageManager -> PythonSimplePackageSpecification(packageName, version, repository ?: findRepositoryForPackage(packageName))
      is CondaPackageManager -> when {
        useConda -> CondaPackageSpecification(packageName, version)
        else -> PythonSimplePackageSpecification(packageName, version, repository ?: findRepositoryForPackage(packageName))
      }
      else -> error("Unknown package manager")
    }
  }

  override fun shouldFetchLatestVersionsForOnlyInstalledPackages(): Boolean = !(isConda && useConda)

  override fun dispose() {
    scope.cancel()
  }

  companion object {
    var runningUnderOldUI = false
  }
}