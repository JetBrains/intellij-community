// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.kotlin.psi

import com.intellij.lang.Language
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor

internal class UastDescriptorLightParameter(
    name: String,
    type: PsiType,
    parent: PsiElement,
    ktParameter: ValueParameterDescriptor,
    language: Language = parent.language,
) : UastDescriptorLightParameterBase<ValueParameterDescriptor>(name, type, parent, ktParameter, language)
