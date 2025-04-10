// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.conda

import com.intellij.openapi.components.service
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonPackageDetails
import com.jetbrains.python.packaging.common.PythonPackageSpecification
import com.jetbrains.python.packaging.common.PythonPackageSpecificationBase
import com.jetbrains.python.packaging.repository.PyPackageRepository
import com.jetbrains.python.packaging.requirement.PyRequirementRelation

class CondaPackage(
  name: String, version: String,
  editableMode: Boolean,
  val installedWithPip: Boolean = false,
) : PythonPackage(name, version, editableMode) {
  override fun toString(): String {
    return "CondaPackage(name='$name', version='$version', installedWithPip=$installedWithPip)"
  }
}

class CondaPackageSpecification(name: String,
                                version: String?,
                                relation: PyRequirementRelation? = null) : PythonPackageSpecificationBase(name, version, relation, CondaPackageRepository) {
  override val repository: PyPackageRepository = CondaPackageRepository
  override var versionSpecs: String? = null
    get() = if (field != null) "${field}" else if (version != null) "${relation?.presentableText ?: "=="}$version" else ""

  override fun buildInstallationString(): List<String> {
    return listOf("$name$versionSpecs")
  }
}

class CondaPackageDetails(override val name: String,
                          override val availableVersions: List<String> = emptyList(),
                          override val summary: String? = null,
                          override val description: String? = null,
                          override val descriptionContentType: String? = null,
                          override val documentationUrl: String? = null)  : PythonPackageDetails {
  override val repository: PyPackageRepository = CondaPackageRepository
  override fun toPackageSpecification(version: String?): PythonPackageSpecification {
    return CondaPackageSpecification(name, version)
  }
}

object CondaPackageRepository : PyPackageRepository("Conda", null, null) {
  override fun createPackageSpecification(packageName: String, version: String?, relation: PyRequirementRelation?): PythonPackageSpecification {
    return CondaPackageSpecification(packageName, version, relation)
  }

  override fun createForcedSpecPackageSpecification(packageName: String, versionSpecs: String?): PythonPackageSpecification {
    val spec = CondaPackageSpecification(packageName, null, null)
    spec.versionSpecs = versionSpecs
    return spec
  }

  override fun getPackages(): Set<String> {
    return service<CondaPackageCache>().packages
  }
}