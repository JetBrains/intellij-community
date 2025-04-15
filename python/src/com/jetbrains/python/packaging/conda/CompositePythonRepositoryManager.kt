// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.conda

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.PyPackageVersion
import com.jetbrains.python.packaging.PyPackageVersionComparator
import com.jetbrains.python.packaging.common.EmptyPythonPackageDetails
import com.jetbrains.python.packaging.common.PythonPackageDetails
import com.jetbrains.python.packaging.common.PythonPackageSpecification
import com.jetbrains.python.packaging.management.PythonPackageManagerService
import com.jetbrains.python.packaging.management.PythonRepositoryManager
import com.jetbrains.python.packaging.repository.PyPackageRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

internal class CompositePythonRepositoryManager(
  override val project: Project,
  private val managers: List<PythonRepositoryManager>,
  @Deprecated("Don't use sdk from here") override val sdk: Sdk
) : PythonRepositoryManager {

  override val repositories: List<PyPackageRepository> =
    managers.flatMap { it.repositories }

  override fun allPackages(): Set<String> =
    repositories.flatMap { it.getPackages() }.toSet()

  override suspend fun getPackageDetails(pkg: PythonPackageSpecification): PythonPackageDetails {
    for (manager in managers) {
      if (manager.allPackages().contains(pkg.name)) {
        return manager.getPackageDetails(pkg)
      }
    }
    return EmptyPythonPackageDetails(pkg.name, PyBundle.message("python.packaging.could.not.parse.response", pkg.name, pkg.repository?.name))
  }

  override suspend fun getLatestVersion(spec: PythonPackageSpecification): PyPackageVersion? {
    var latestVersion: PyPackageVersion? = null
    for (manager in managers) {
      val version = manager.getLatestVersion(spec)
      if (version != null && (latestVersion == null || PyPackageVersionComparator.compare(version, latestVersion) > 0)) {
        latestVersion = version
      }
    }
    return latestVersion
  }

  private val mutex = Mutex()
  private val isInit = AtomicBoolean(false)
  private val cacheRefreshLimit = managers.size * 2
  private val cacheRefreshLimitSemaphore = Semaphore(cacheRefreshLimit)

  override suspend fun refreshCaches() {
    mutex.withLock {
      managers.forEach { manager ->
        launchManagerRefresh(manager)
      }
      isInit.set(true)
    }
  }

  private fun launchManagerRefresh(manager: PythonRepositoryManager) {
    project.service<PythonPackageManagerService>().getServiceScope().launch {
      cacheRefreshLimitSemaphore.withPermit {
        manager.refreshCaches()
      }
    }
  }
  @Throws(IOException::class)
  override suspend fun initCaches() {
    if (!isInit.compareAndSet(false, true)) return
    mutex.withLock {
      managers.forEach { it.initCaches() }
    }
  }

  override fun buildPackageDetails(rawInfo: String?, spec: PythonPackageSpecification): PythonPackageDetails {
    val repositoryWithPackage = managers.firstOrNull { it ->
      it.allPackages().contains(spec.name)
    } ?: error("No repository contains the package ${spec.name}")

    return repositoryWithPackage.buildPackageDetails(rawInfo, spec)
  }

  override fun searchPackages(query: String, repository: PyPackageRepository): List<String> {
    return managers.flatMap { it.searchPackages(query, repository) }
  }

  override fun searchPackages(query: String): Map<PyPackageRepository, List<String>> {
    return managers.flatMap { it.searchPackages(query).entries }
      .groupBy({ it.key }, { it.value })
      .mapValues { it.value.flatten() }
  }
}