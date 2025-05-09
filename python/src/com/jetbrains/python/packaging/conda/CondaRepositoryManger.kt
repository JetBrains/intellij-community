// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.conda

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.packaging.pip.PipBasedRepositoryManager
import com.jetbrains.python.packaging.repository.PyPackageRepository
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal class CondaRepositoryManger(
  override val project: Project,
  @Deprecated("Don't use sdk from here") val sdk: Sdk,
) : PipBasedRepositoryManager() {

  override val repositories: List<PyPackageRepository>
    get() = listOf(CondaPackageRepository) + super.repositories

  override suspend fun refreshCaches() {
    super.refreshCaches()
    service<CondaPackageCache>().forceReloadCache(sdk, project)
  }

  override suspend fun initCaches() {
    super.initCaches()
    service<CondaPackageCache>().reloadCache(sdk, project)
  }
}