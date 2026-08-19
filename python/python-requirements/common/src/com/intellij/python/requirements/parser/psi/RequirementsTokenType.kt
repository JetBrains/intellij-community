// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.requirements.parser.psi

import com.intellij.psi.tree.IElementType
import com.intellij.python.requirements.RequirementsLanguage

class RequirementsTokenType(debugName: String) : IElementType(debugName, RequirementsLanguage) {
  override fun toString(): String {
    return "RequirementsTokenType." + super.toString()
  }
}