// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements.psi

import com.intellij.psi.PsiElement
import com.jetbrains.python.requirements.RequirementType

interface Requirement : PsiElement {

  val type: RequirementType

  val displayName: String

  val requirement: String

  fun enabled(values: Map<String, String?>): Boolean

}