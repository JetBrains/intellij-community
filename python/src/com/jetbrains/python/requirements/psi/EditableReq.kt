// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.jetbrains.python.requirements.psi

import com.jetbrains.python.requirements.RequirementType

interface EditableReq : NamedElement, Requirement {
  val uriReference: UriReference?

  override fun enabled(values: Map<String, String?>): Boolean {
    return true
  }

  override val displayName: String
    get() {
      return uriReference?.text ?: ""
    }

  override val type: RequirementType
    get() = RequirementType.EDITABLE
}
