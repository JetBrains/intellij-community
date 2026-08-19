// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.python.requirements

import com.intellij.openapi.util.NlsSafe
import com.jetbrains.python.packaging.PyPackage
import com.jetbrains.python.packaging.PyPackageName
import com.jetbrains.python.packaging.PyRequirement
import com.jetbrains.python.packaging.requirement.PyRequirementEnvMarker
import com.jetbrains.python.packaging.requirement.PyRequirementEnvMarkerType
import com.jetbrains.python.packaging.requirement.PyRequirementVersionSpec
import org.jetbrains.annotations.ApiStatus

/**
 * This class is not an API, consider using methods listed below.
 *
 * @see PyRequirementParser.fromText
 * @see PyRequirementParser.fromLine
 * @see PyRequirementParser.fromFile
 */
@ApiStatus.Internal
class PyRequirementImpl(
  private val presentableName: String,
  private val versionSpecs: List<PyRequirementVersionSpec>,
  private val installOptions: List<String>,
  private val extras: String,
  private val environmentMarker: PyRequirementEnvMarker?,
  private val urlReference: String?
) : PyRequirement {
  constructor(
    presentableName: String,
    versionSpecs: List<PyRequirementVersionSpec>,
    installOptions: List<String>,
    extras: String,
    environmentMarker: PyRequirementEnvMarker?
  ) : this(presentableName, versionSpecs, installOptions, extras, environmentMarker, null)

  constructor(
    presentableName: String,
    versionSpecs: List<PyRequirementVersionSpec>,
    installOptions: List<String>,
    extras: String
  ) : this(presentableName, versionSpecs, installOptions, extras, null, null)

  private val packageName: PyPackageName = PyPackageName.from(presentableName)
  private val name: String = packageName.name

  override fun getName(): String = name
  override fun getPackageName(): PyPackageName = packageName

  override fun getExtras(): String = extras
  override fun getVersionSpecs(): List<PyRequirementVersionSpec> = versionSpecs
  override fun getInstallOptions(): List<String> = installOptions
  override fun getEnvironmentMarker(): PyRequirementEnvMarker? = environmentMarker
  override fun getUrlReference(): String? = urlReference

  override fun getPresentableTextWithoutVersion(): @NlsSafe String = presentableName

  override fun match(packages: Collection<PyPackage>): PyPackage? {
    return packages.firstOrNull { this.match(it) }
  }

  override fun match(packageName: PyPackage): Boolean {
    return name == PyPackageName.normalizePackageName(packageName.name) && versionSpecs
      .all { it.matches(packageName.version) }
  }

  override fun appliesTo(platformData: Map<PyRequirementEnvMarkerType, String>): Boolean {
    return environmentMarker?.matches(platformData) ?: true
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) {
      return true
    }

    return when (other) {
      is String -> name == PyPackageName.normalizePackageName(other)
      is PyRequirementImpl -> name == other.name &&
                              versionSpecs == other.versionSpecs &&
                              environmentMarker == other.environmentMarker &&
                              urlReference == other.urlReference
      else -> false
    }
  }

  override fun withVersionSpecs(specs: List<PyRequirementVersionSpec>): PyRequirement {
    return PyRequirementImpl(presentableName, specs, installOptions, extras, environmentMarker, urlReference)
  }

  override fun hashCode(): Int = 31 * (31 * name.hashCode() + versionSpecs.hashCode()) +
                                 (environmentMarker?.hashCode() ?: 0)

  override fun toString(): String {
    return presentableText
  }
}