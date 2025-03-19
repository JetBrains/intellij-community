// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements.psi

import com.intellij.psi.tree.IElementType
import com.jetbrains.python.requirements.RequirementsLanguage
import org.jetbrains.annotations.NonNls


class RequirementsElementType(debugName: @NonNls String) : IElementType(debugName, RequirementsLanguage.INSTANCE)