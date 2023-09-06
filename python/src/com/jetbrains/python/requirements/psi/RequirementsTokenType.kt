package com.jetbrains.python.requirements.psi

import com.intellij.psi.tree.IElementType
import com.jetbrains.python.requirements.RequirementsLanguage
import org.jetbrains.annotations.NonNls

class RequirementsTokenType(debugName: @NonNls String) : IElementType(debugName, RequirementsLanguage.INSTANCE) {
  override fun toString(): String {
    return "RequirementsTokenType." + super.toString()
  }
}