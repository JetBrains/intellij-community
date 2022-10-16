// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.common

import com.jetbrains.python.packaging.repository.PyEmptyPackagePackageRepository
import com.jetbrains.python.packaging.repository.PyPackageRepository
import org.jetbrains.annotations.Nls

open class PythonPackage(val name: String, val version: String)

interface PythonPackageDetails {

  val name: String
  val availableVersions: List<String>
  val repository: PyPackageRepository
  val summary: String?
  val description: String?
  val descriptionContentType: String?
  val documentationUrl: String?
  fun toPackageSpecification(version: String? = null): PythonPackageSpecification
}

data class PythonSimplePackageDetails(
  override val name: String,
  override val availableVersions: List<String> = emptyList(),
  override val repository: PyPackageRepository,
  override val summary: String? = null,
  @Nls override val description: String? = null,
  override val descriptionContentType: String? = null,
  override val documentationUrl: String? = null) : PythonPackageDetails {

  override fun toPackageSpecification(version: String?): PythonSimplePackageSpecification {
    return PythonSimplePackageSpecification(name, version, repository)
  }
}

class EmptyPythonPackageDetails(override val name: String, @Nls override val description: String? = null) : PythonPackageDetails {
  override val availableVersions: List<String> = emptyList()
  override val repository: PyPackageRepository = PyEmptyPackagePackageRepository
  override val summary: String? = null
  override val descriptionContentType: String? = null
  override val documentationUrl: String? = null
  override fun toPackageSpecification(version: String?) = error("Using EmptyPythonPackageDetails for specification")
}

interface PythonPackageSpecification {
  // todo[akniazev]: add version specs and use them in buildInstallationString
  val name: String
  val version: String?
  val repository: PyPackageRepository?

  fun buildInstallationString(): List<String> {
    return listOf("$name${if (version != null) "==$version" else ""}")
  }
}

interface PythonLocationBasedPackageSpecification : PythonPackageSpecification {
  val location: String
  val editable: Boolean
  val prefix: String
  override val version: String?
    get() = null
  override val repository: PyPackageRepository?
    get() = null
  override fun buildInstallationString(): List<String> = if (editable) listOf("-e", "$prefix$location") else listOf("$prefix$location")
}

data class PythonSimplePackageSpecification(override val name: String,
                                            override val version: String?,
                                            override val repository: PyPackageRepository?) : PythonPackageSpecification

data class PythonLocalPackageSpecification(override val name: String,
                                           override val location: String,
                                           override val editable: Boolean) : PythonLocationBasedPackageSpecification {
  override val prefix: String = "file://"
}

data class PythonVcsPackageSpecification(override val name: String,
                                         override val location: String,
                                         override val prefix: String,
                                         override val editable: Boolean) : PythonLocationBasedPackageSpecification

