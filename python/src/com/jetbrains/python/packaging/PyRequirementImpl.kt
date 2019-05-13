// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging

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
data class PyRequirementImpl(private val name: String,
                             private val versionSpecs: List<PyRequirementVersionSpec>,
                             private val installOptions: List<String>,
                             private val extras: String) : PyRequirement {

  override fun getName(): String = name
  override fun getExtras(): String = extras
  override fun getVersionSpecs(): List<PyRequirementVersionSpec> = versionSpecs
  override fun getInstallOptions(): List<String> = installOptions

  override fun match(packages: Collection<PyPackage>): PyPackage? {
    val normalizedName = name.replace('_', '-')
    return packages.firstOrNull { normalizedName.equals(it.name, true) && versionSpecs.all { spec -> spec.matches(it.version) } }
  }
}
