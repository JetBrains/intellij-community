// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.common

import com.jetbrains.python.packaging.*
import com.jetbrains.python.packaging.management.findPackageSpecification
import com.jetbrains.python.packaging.repository.PyPackageRepository
import com.jetbrains.python.packaging.requirement.PyRequirementVersionSpec
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.Icon

open class PythonPackage(name: String, val version: String, val isEditableMode: Boolean) {
  companion object {
    private const val HASH_MULTIPLIER = 31
  }

  @ApiStatus.Internal
  val normalizedName: PyPackageName = PyPackageName.from(name)

  val name: String = normalizedName.name
  val presentableName: String = name

  @ApiStatus.Internal
  open val sourceRepoIcon: Icon? = null

  override fun toString(): String {
    return "PythonPackage(name='$name', version='$version')"
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is PythonPackage) return false
    return name == other.name && version == other.version && isEditableMode == other.isEditableMode
  }

  override fun hashCode(): Int {
    var result = name.hashCode()
    result = HASH_MULTIPLIER * result + version.hashCode()
    result = HASH_MULTIPLIER * result + isEditableMode.hashCode()
    return result
  }

  @ApiStatus.Internal
  fun matches(requirement: PyRequirement): Boolean {
    return toPyPackage().matches(requirement)
  }

  @ApiStatus.Internal
  fun toPyPackage(): PyPackage {
    return PyPackage(name, version)
  }
}

class PythonOutdatedPackage(name: String, version: String, val latestVersion: String)
  : PythonPackage(name, version, false) {
  override fun toString(): String = "PythonOutdatedPackage(name='$name', version='$version', latestVersion='$latestVersion')"

  @ApiStatus.Internal
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    if (!super.equals(other)) return false

    other as PythonOutdatedPackage

    return latestVersion == other.latestVersion
  }

  @ApiStatus.Internal
  override fun hashCode(): Int {
    var result = super.hashCode()
    result = 31 * result + latestVersion.hashCode()
    return result
  }
}

interface PythonPackageDetails {
  val name: String
  val availableVersions: List<String>
  val repository: PyPackageRepository
  val summary: String?
  val description: String?
  val descriptionContentType: String?
  val documentationUrl: String?
  fun toPackageSpecification(version: String? = null): PythonRepositoryPackageSpecification? =
    repository.findPackageSpecification(pyRequirement(name, version?.let { pyRequirementVersionSpec(it) }))
}

@ApiStatus.Internal
data class PythonSimplePackageDetails(
  override val name: String,
  override val availableVersions: List<String> = emptyList(),
  override val repository: PyPackageRepository,
  override val summary: String? = null,
  override val description: @Nls String? = null,
  override val descriptionContentType: String? = null,
  override val documentationUrl: String? = null,
  val author: String? = null,
  val authorEmail: String? = null,
  val homepageUrl: String? = null,
) : PythonPackageDetails

/**
 * Please use one of the following factory methods:
 *
 * 1) [com.jetbrains.python.packaging.management.PythonPackageManager.findPackageSpecification]
 *    Use this method if you have a package manager instance or an SDK.
 *    It will locate the appropriate repository for the package from the available options.
 *
 * 2) [PyPackageRepository.findPackageSpecification]
 *    Use this method if you already have a specific repository instance and want to look up within it only.
 *    The following well-known public repositories are also available for direct access:
 *   - PyPI (https://pypi.org):  [com.jetbrains.python.packaging.repository.PyPIPackageRepository.findPackageSpecification]
 *   - Conda: [com.jetbrains.python.packaging.conda.CondaPackageRepository.findPackageSpecification]
 */
@ApiStatus.Internal
data class PythonRepositoryPackageSpecification(
  val repository: PyPackageRepository,
  val requirement: PyRequirement,
) {
  val name: String = requirement.name
  val versionSpec: PyRequirementVersionSpec? = requirement.versionSpecs.firstOrNull()

  val nameWithVersionSpec: String
    get() = "$name${versionSpec?.presentableText ?: ""}"

  val nameWithVersionsSpec: String
    get() {
      val versionSpecsString = requirement.versionSpecs.joinToString(",") { it.presentableText }
      return "$name${versionSpecsString}"
    }


  constructor(
    repository: PyPackageRepository,
    packageName: String,
    version: String? = null,
  ) : this(
    repository = repository,
    requirement = pyRequirement(packageName, version?.let { pyRequirementVersionSpec(it) }),
  )
}

@Deprecated(
  "Use RepositoryPythonPackageSpecification instead",
  replaceWith = ReplaceWith("RepositoryPythonPackageSpecification"),
  level = DeprecationLevel.ERROR
)
interface PythonPackageSpecification

@Deprecated(
  "Use RepositoryPythonPackageSpecification instead",
  replaceWith = ReplaceWith("RepositoryPythonPackageSpecification"),
  level = DeprecationLevel.ERROR
)
interface PythonSimplePackageSpecification
