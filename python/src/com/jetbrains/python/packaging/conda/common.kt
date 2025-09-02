// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.conda

import com.jetbrains.python.PyBundle
import com.jetbrains.python.icons.PythonIcons
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonPackageDetails
import com.jetbrains.python.packaging.repository.PyPackageRepository
import com.jetbrains.python.psi.icons.PythonPsiApiIcons
import javax.swing.Icon

class CondaPackage(
  name: String, version: String,
  editableMode: Boolean,
  val installedWithPip: Boolean = false,
) : PythonPackage(name, version, editableMode) {
  override val sourceRepoIcon: Icon = if (installedWithPip) {
    PythonPsiApiIcons.Python
  } else {
    PythonIcons.Python.Anaconda
  }

  override fun toString(): String {
    return "CondaPackage(name='$name', version='$version', installedWithPip=$installedWithPip)"
  }
}

class CondaPackageDetails(
  override val name: String,
  override val availableVersions: List<String> = emptyList(),
  override val summary: String? = null,
  override val description: String? = null,
  override val descriptionContentType: String? = null,
  override val documentationUrl: String? = null,
) : PythonPackageDetails {
  override val repository: PyPackageRepository = CondaPackageRepository
}

fun PythonPackageDetails?.toCondaPackageDetails(packageName: String, availableVersions: List<String>): CondaPackageDetails = when (this) {
  null -> CondaPackageDetails(
    name = packageName,
    availableVersions = availableVersions,
    summary = PyBundle.message("conda.packaging.empty.pypi.info")
  )
  else -> CondaPackageDetails(
    availableVersions = availableVersions,
    name = packageName,
    summary = summary,
    description = description,
    descriptionContentType = descriptionContentType,
    documentationUrl = documentationUrl
  )
}
