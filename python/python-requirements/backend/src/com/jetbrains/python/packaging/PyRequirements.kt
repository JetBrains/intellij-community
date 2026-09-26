// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging

import com.jetbrains.python.packaging.requirement.PyRequirementRelation
import com.jetbrains.python.packaging.requirement.PyRequirementVersionSpec
import com.intellij.python.requirements.pyRequirement as pyRequirementNew
import com.intellij.python.requirements.pyRequirementVersionSpec as pyRequirementVersionSpecNew

@Deprecated(
  "This function has been moved to the com.intellij.python.requirements package.",
  replaceWith = ReplaceWith("pyRequirement", imports = ["com.intellij.python.requirements.pyRequirement"])
)
fun pyRequirement(name: String, relation: PyRequirementRelation, version: String): PyRequirement = pyRequirementNew(name, relation, version)

@Deprecated(
  "This function has been moved to the com.intellij.python.requirements package.",
  replaceWith = ReplaceWith("pyRequirement", imports = ["com.intellij.python.requirements.pyRequirementVersionSpec"])
)
fun pyRequirementVersionSpec(relation: PyRequirementRelation, version: String): PyRequirementVersionSpec =
  pyRequirementVersionSpecNew(relation, version)
