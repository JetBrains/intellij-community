// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging

import com.intellij.openapi.util.NlsSafe
import com.jetbrains.python.packaging.requirement.PyRequirementVersionSpec

/**
 * This class is not an API, consider using methods listed below.
 *
 * @see PyRequirementParser.fromText
 * @see PyRequirementParser.fromLine
 * @see PyRequirementParser.fromFile
 */
class PyRequirementImpl(
  private val presentableName: String,
  private val versionSpecs: List<PyRequirementVersionSpec>,
  private val installOptions: List<String>,
  private val extras: String,
) : PyRequirement {

  private val name: String = normalizePackageName(presentableName)

  override fun getName(): String = name
  override fun getExtras(): String = extras
  override fun getVersionSpecs(): List<PyRequirementVersionSpec> = versionSpecs
  override fun getInstallOptions(): List<String> = installOptions
  override fun getPresentableTextWithoutVersion(): @NlsSafe String = presentableName

  override fun match(packages: Collection<PyPackage>): PyPackage? {
    return packages.firstOrNull { this.match(it) }
  }

  override fun match(packageName: PyPackage): Boolean {
    return name == normalizePackageName(packageName.name) && versionSpecs
      .all { it.matches(packageName.version) }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) {
      return true
    }

    return when (other) {
      is String -> name == normalizePackageName(other)
      is PyRequirementImpl -> name == other.name && versionSpecs == other.versionSpecs
      else -> false
    }
  }

  override fun withVersionSpecs(specs: List<PyRequirementVersionSpec>): PyRequirement {
    return PyRequirementImpl(presentableName, specs, installOptions, extras)
  }

  override fun hashCode(): Int = 31 * name.hashCode() + versionSpecs.hashCode()

  override fun toString(): String {
    return presentableText
  }
}