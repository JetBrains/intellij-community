// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.common

import com.intellij.openapi.diagnostic.thisLogger
import com.jetbrains.python.packaging.repository.PyEmptyPackagePackageRepository
import com.jetbrains.python.packaging.repository.PyPIPackageRepository
import com.jetbrains.python.packaging.repository.PyPackageRepository
import com.jetbrains.python.packaging.requirement.PyRequirementRelation
import org.jetbrains.annotations.Nls

open class PythonPackage(val name: String, val version: String, val isEditableMode: Boolean) {
  companion object {
    private const val HASH_MULTIPLIER = 31
  }

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
}

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
  override val documentationUrl: String? = null,
  val author: String? = null,
  val authorEmail: String? = null,
  val homepageUrl: String? = null) : PythonPackageDetails {

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

open class PythonPackageSpecificationBase(override val name: String,
                                          val version: String?,
                                          val relation: PyRequirementRelation? = null,
                                          override val repository: PyPackageRepository?) : PythonPackageSpecification {
  override val versionSpecs: String?
    get() = if (version != null) "${relation?.presentableText ?: "=="}$version" else ""
}

interface PythonPackageSpecification {
  // todo[akniazev]: add version specs and use them in buildInstallationString
  val name: String
  val repository: PyPackageRepository?
  val versionSpecs: String?

  fun buildInstallationString(): List<String> = buildList {
    add("$name$versionSpecs")
    if (repository == PyEmptyPackagePackageRepository) {
      thisLogger().warn("PyEmptyPackagePackageRepository used as source repository for package installation!")
      return@buildList
    }
    if (repository != null && repository != PyPIPackageRepository) {
      add("--index-url")
      add(repository!!.urlForInstallation)
    }
  }
}

interface PythonLocationBasedPackageSpecification : PythonPackageSpecification {
  val location: String
  val editable: Boolean
  val prefix: String
  override val repository: PyPackageRepository?
    get() = null
  override val versionSpecs: String?
    get() = null
  override fun buildInstallationString(): List<String> = if (editable) listOf("-e", "$prefix$location") else listOf("$prefix$location")
}

data class PythonSimplePackageSpecification(override val name: String,
                                            val version: String?,
                                            override val repository: PyPackageRepository?,
                                            val relation: PyRequirementRelation? = null) : PythonPackageSpecification {
  override var versionSpecs: String? = null
    get() = if (field != null) field else if (version != null) "${relation?.presentableText ?: "=="}$version" else ""
}

data class PythonLocalPackageSpecification(override val name: String,
                                           override val location: String,
                                           override val editable: Boolean) : PythonLocationBasedPackageSpecification {
  override val prefix: String = "file://"
}

data class PythonVcsPackageSpecification(override val name: String,
                                         override val location: String,
                                         override val prefix: String,
                                         override val editable: Boolean) : PythonLocationBasedPackageSpecification

fun normalizePackageName(name: String): String {
  return name.replace(Regex("[-_.]+"), "-").lowercase()
}