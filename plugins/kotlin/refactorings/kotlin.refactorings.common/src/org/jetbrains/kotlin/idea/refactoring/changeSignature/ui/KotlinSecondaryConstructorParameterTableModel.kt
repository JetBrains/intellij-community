// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.changeSignature.ui

import com.intellij.psi.PsiElement
import com.intellij.refactoring.changeSignature.ParameterTableModelItemBase
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinModifiableMethodDescriptor
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinModifiableParameterInfo

abstract class KotlinSecondaryConstructorParameterTableModel<P: KotlinModifiableParameterInfo, V>(
    methodDescriptor: KotlinModifiableMethodDescriptor<P, V>,
    defaultValueContext: PsiElement
) : KotlinCallableParameterTableModel<P, V>(
  methodDescriptor,
  defaultValueContext,
  NameColumn<P, ParameterTableModelItemBase<P>>(defaultValueContext.project),
  TypeColumn<P, ParameterTableModelItemBase<P>>(defaultValueContext.project, KotlinFileType.INSTANCE),
  DefaultValueColumn<P, ParameterTableModelItemBase<P>>(
        defaultValueContext.project,
        KotlinFileType.INSTANCE
    ),
  DefaultParameterColumn(),
)