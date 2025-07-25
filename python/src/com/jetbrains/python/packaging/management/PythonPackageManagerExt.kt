// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("PythonPackageManagerExt")

package com.jetbrains.python.packaging.management

import com.jetbrains.python.PyBundle
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.PyRequirement
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonRepositoryPackageSpecification
import com.jetbrains.python.packaging.normalizePackageName
import com.jetbrains.python.packaging.pyRequirement
import com.jetbrains.python.packaging.pyRequirementVersionSpec
import com.jetbrains.python.packaging.repository.PyPackageRepository
import com.jetbrains.python.packaging.requirement.PyRequirementRelation
import com.jetbrains.python.packaging.requirement.PyRequirementVersionSpec
import org.jetbrains.annotations.ApiStatus


@ApiStatus.Internal
suspend fun PythonPackageManager.installPackages(vararg packages: String): PyResult<List<PythonPackage>> {
  waitForInit()
  val specifications = packages.map {
    findPackageSpecification(normalizePackageName(it))
    ?: return PyResult.localizedError(PyBundle.message("python.packaging.installing.error.failed.to.find.specification", it))
  }
  return installPackage(PythonPackageInstallRequest.ByRepositoryPythonPackageSpecifications(specifications))
}


@ApiStatus.Internal
fun PythonPackageManager.getInstalledPackageSnapshot(packageName: String, version: String? = null): PythonPackage? {
  val normalizedPackage = normalizePackageName(packageName)
  return listInstalledPackagesSnapshot().firstOrNull { it.name == normalizedPackage && (version == null || version == it.version) }
}

@ApiStatus.Internal
fun PythonPackageManager.hasInstalledPackageSnapshot(packageName: String, version: String? = null): Boolean =
  getInstalledPackageSnapshot(packageName, version) != null


@ApiStatus.Internal
suspend fun PythonPackageManager.findPackageSpecification(
  packageName: String,
  versionSpec: PyRequirementVersionSpec? = null,
): PythonRepositoryPackageSpecification? {
  return repositoryManager.findPackageSpecification(pyRequirement(packageName, versionSpec))
}

@ApiStatus.Internal
suspend fun PythonPackageManager.findPackageSpecification(
  requirement: PyRequirement,
  repository: PyPackageRepository? = null,
): PythonRepositoryPackageSpecification? {
  return repositoryManager.findPackageSpecification(requirement, repository)
}


@ApiStatus.Internal
suspend fun PythonPackageManager.findPackageSpecification(
  packageName: String,
  version: String? = null,
  relation: PyRequirementRelation = PyRequirementRelation.EQ,
): PythonRepositoryPackageSpecification? {
  val versionSpec = version?.let { pyRequirementVersionSpec(relation, version) }
  return findPackageSpecification(pyRequirement(packageName, versionSpec))
}

@ApiStatus.Internal
suspend fun PythonPackageManager.hasInstalledPackage(pyPackage: PythonPackage): Boolean =
  getInstalledPackage(pyPackage.name, pyPackage.version) != null


@ApiStatus.Internal
suspend fun PythonPackageManager.hasInstalledPackage(packageName: String, version: String? = null): Boolean =
  getInstalledPackage(packageName, version) != null

@ApiStatus.Internal
suspend fun PythonPackageManager.getInstalledPackage(packageName: String, version: String? = null): PythonPackage? {
  waitForInit()
  return getInstalledPackageSnapshot(packageName, version)
}

@ApiStatus.Internal
fun PythonRepositoryManager.packagesByRepository(): Sequence<Pair<PyPackageRepository, Set<String>>> {
  return repositories.asSequence().map { it to it.getPackages() }
}