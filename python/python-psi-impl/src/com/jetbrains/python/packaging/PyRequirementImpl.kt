// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging

import com.intellij.openapi.util.NlsSafe
import com.jetbrains.python.packaging.requirement.PyRequirementVersionSpec

/**
 * This class is not an API, consider using methods listed below.
 *
 * @see PyPackageManager.parseRequirement
 * @see PyPackageManager.parseRequirements
 *
 * @see PyRequirementParser.fromText
 * @see PyRequirementParser.fromLine
 * @see PyRequirementParser.fromFile
 */
data class PyRequirementImpl(
  private val name: String,
  private val versionSpecs: List<PyRequirementVersionSpec>,
  private val installOptions: List<String>,
  private val extras: String,
) : PyRequirement {

  override fun getName(): String = NormalizedPackageName.from(name).name
  override fun getExtras(): String = extras
  override fun getVersionSpecs(): List<PyRequirementVersionSpec> = versionSpecs
  override fun getInstallOptions(): List<String> = installOptions
  override fun getPresentableTextWithoutVersion(): @NlsSafe String = name

  override fun match(packages: Collection<PyPackage>): PyPackage? {
    return packages.firstOrNull { pkg ->
      isPackageNameEqual(pkg.name) && versionSpecs.all { it.matches(pkg.version) }
    }
  }

  private fun isPackageNameEqual(otherName: String): Boolean {
    return normalizePackageName(name) == normalizePackageName(otherName)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    return when (other) {
      is String -> getName() == NormalizedPackageName.from(other).name
      is PyRequirementImpl -> getName().equals(other.getName(), ignoreCase = true)
      else -> false
    }
  }

  override fun hashCode(): Int = normalizePackageName(name).hashCode()

  @JvmInline
  value class NormalizedPackageName private constructor(val name: String) {
    companion object {
      fun from(name: String): NormalizedPackageName =
        NormalizedPackageName(normalizePackageName(name))
    }
  }
}