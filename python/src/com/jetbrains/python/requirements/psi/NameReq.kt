// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.jetbrains.python.requirements.psi

import com.jetbrains.python.requirements.RequirementType

interface NameReq : NamedElement, Requirement {
  val name: SimpleName

  val extras: Extras?

  val hashOptionList: List<HashOption?>

  val quotedMarker: QuotedMarker?

  val versionspec: Versionspec?

  override fun enabled(values: Map<String, String?>): Boolean {
    return quotedMarker?.logical()?.check(values) ?: true
  }

  override val displayName: String
    get() {
      return name.text
    }

  override val type: RequirementType
    get() = RequirementType.NAME

  fun setVersion(newVersion: String)
}
