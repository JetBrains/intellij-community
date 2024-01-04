// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.requirements.psi

import com.intellij.psi.tree.IElementType
import com.intellij.python.community.impl.requirements.RequirementsLanguage
import org.jetbrains.annotations.NonNls

class RequirementsTokenType(debugName: @NonNls String) : IElementType(debugName, RequirementsLanguage.INSTANCE) {
  override fun toString(): String {
    return "RequirementsTokenType." + super.toString()
  }
}