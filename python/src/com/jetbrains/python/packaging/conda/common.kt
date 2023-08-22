// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.conda

import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonPackageDetails
import com.jetbrains.python.packaging.common.PythonPackageSpecification
import com.jetbrains.python.packaging.repository.PyPackageRepository
import com.jetbrains.python.packaging.requirement.PyRequirementRelation

class CondaPackage(name: String, version: String, val installedWithPip: Boolean = false) : PythonPackage(name, version) {
  override fun toString(): String {
    return "CondaPackage(name='$name', version='$version', installedWithPip=$installedWithPip)"
  }
}

class CondaPackageSpecification(override val name: String,
                                override val version: String?,
                                override val relation: PyRequirementRelation? = null) : PythonPackageSpecification {
  override val repository: PyPackageRepository = CondaPackageRepository

  override fun buildInstallationString(): List<String> {
    return listOf("$name${if (version != null) "=$version" else ""}")
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

object CondaPackageRepository : PyPackageRepository("Conda", "", "") {
  override fun createPackageSpecification(packageName: String, version: String?, relation: PyRequirementRelation?): PythonPackageSpecification {
    return CondaPackageSpecification(packageName, version, relation)
  }
}