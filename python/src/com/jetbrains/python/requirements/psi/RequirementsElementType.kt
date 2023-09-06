package com.jetbrains.python.requirements.psi

import com.intellij.psi.tree.IElementType
import com.jetbrains.python.requirements.RequirementsLanguage
import org.jetbrains.annotations.NonNls


class RequirementsElementType(debugName: @NonNls String) : IElementType(debugName, RequirementsLanguage.INSTANCE)