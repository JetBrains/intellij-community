// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging

import com.intellij.openapi.util.NlsSafe
import com.intellij.python.requirements.PyPackageVersion
import com.intellij.python.requirements.pyRequirement as pyRequirementNew
import com.intellij.python.requirements.pyRequirementVersionSpec as pyRequirementVersionSpecNew
import com.jetbrains.python.packaging.requirement.PyRequirementRelation
import com.jetbrains.python.packaging.requirement.PyRequirementVersionSpec
import org.jetbrains.annotations.ApiStatus

@Deprecated(
  "This function has been moved to the com.intellij.python.requirements package.",
  replaceWith = ReplaceWith("pyRequirement", imports = ["com.intellij.python.requirements.pyRequirement"])
)
@ApiStatus.Internal
fun pyRequirement(name: String, versionSpecs: List<PyRequirementVersionSpec>, extras: List<String>): PyRequirement =
  pyRequirementNew(name, versionSpecs, extras, null)

@Deprecated(
  "This function has been moved to the com.intellij.python.requirements package.",
  replaceWith = ReplaceWith("pyRequirement", imports = ["com.intellij.python.requirements.pyRequirement"])
)
@ApiStatus.Internal
fun pyRequirement(name: String, versionSpec: PyRequirementVersionSpec? = null): PyRequirement = pyRequirementNew(name, versionSpec)

@Deprecated(
  "This function has been moved to the com.intellij.python.requirements package.",
  replaceWith = ReplaceWith("pyRequirement", imports = ["com.intellij.python.requirements.pyRequirement"])
)
@ApiStatus.Internal
fun pyRequirement(name: String, versionSpec: PyRequirementVersionSpec?, extras: String): PyRequirement =
  pyRequirementNew(name, versionSpec, extras)

@Deprecated(
  "This function has been moved to the com.intellij.python.requirements package.",
  replaceWith = ReplaceWith("pyRequirement", imports = ["com.intellij.python.requirements.pyRequirement"])
)
@ApiStatus.Internal
fun pyRequirement(name: String, relation: PyRequirementRelation, version: String, extras: String = ""): PyRequirement =
  pyRequirementNew(name, relation, version, extras)

@Deprecated(
  "This function has been moved to the com.intellij.python.requirements package.",
  replaceWith = ReplaceWith("pyRequirement", imports = ["com.intellij.python.requirements.pyRequirement"])
)
fun pyRequirement(name: String, relation: PyRequirementRelation, version: String): PyRequirement = pyRequirementNew(name, relation, version)

@Deprecated(
  "This function has been moved to the com.intellij.python.requirements package.",
  replaceWith = ReplaceWith("pyRequirement", imports = ["com.intellij.python.requirements.pyRequirementVersionSpec"])
)
@ApiStatus.Internal
fun pyRequirementVersionSpec(relationWithVersion: @NlsSafe String): PyRequirementVersionSpec =
  pyRequirementVersionSpecNew(relationWithVersion)

@Deprecated(
  "This function has been moved to the com.intellij.python.requirements package.",
  replaceWith = ReplaceWith("pyRequirement", imports = ["com.intellij.python.requirements.pyRequirementVersionSpec"])
)
fun pyRequirementVersionSpec(relation: PyRequirementRelation, version: String): PyRequirementVersionSpec =
  pyRequirementVersionSpecNew(relation, version)

@Deprecated(
  "This function has been moved to the com.intellij.python.requirements package.",
  replaceWith = ReplaceWith("pyRequirement", imports = ["com.intellij.python.requirements.pyRequirementVersionSpec"])
)
@ApiStatus.Internal
fun pyRequirementVersionSpec(relation: PyRequirementRelation, version: PyPackageVersion): PyRequirementVersionSpec =
  pyRequirementVersionSpecNew(relation, version)
