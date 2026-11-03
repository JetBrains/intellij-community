// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.backend.packaging

import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.isSuccess
import com.jetbrains.python.packaging.PyRequirement
import com.jetbrains.python.packaging.cache.PythonPackageSearchResult
import com.jetbrains.python.packaging.common.PythonPackageDetails
import com.jetbrains.python.packaging.common.ProjectUrl
import com.jetbrains.python.packaging.conda.CondaPackageRepository
import com.jetbrains.python.packaging.repository.PyPackageRepository

internal class PixiNativeRepository(private val manager: PixiRepositoryManager) :
  PyPackageRepository(CondaPackageRepository.name, null, null) {
  @Volatile
  internal var packageCount: Int = 0

  override fun getSize(): Int = packageCount

  override fun getProjectUrl(packageName: String): ProjectUrl = CondaPackageRepository.getProjectUrl(packageName)

  override var enabled: Boolean
    get() = CondaPackageRepository.enabled
    set(value) {
      CondaPackageRepository.enabled = value
    }

  override suspend fun hasPackage(name: String): Boolean = manager.getPackageDetails(name, this).isSuccess

  override suspend fun hasPackage(pyPackage: PyRequirement): Boolean = manager.findPackageSpecification(pyPackage, this) != null

  override suspend fun search(needle: String, pageSize: Int): PythonPackageSearchResult = manager.searchPackages(this, needle, pageSize)

  override suspend fun buildPackageDetails(packageName: String): PyResult<PythonPackageDetails> = manager.getPackageDetails(packageName, this)
}

internal fun PyPackageRepository?.isPixiNativeRepository(): Boolean = this == CondaPackageRepository || this is PixiNativeRepository
