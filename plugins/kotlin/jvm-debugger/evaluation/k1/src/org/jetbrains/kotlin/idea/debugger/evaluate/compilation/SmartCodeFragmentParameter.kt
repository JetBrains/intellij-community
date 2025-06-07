// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.evaluate.compilation

import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.types.KotlinType

class SmartCodeFragmentParameter(
    val dumb: CodeFragmentParameter.Dumb,
    val targetType: KotlinType,
    val targetDescriptor: DeclarationDescriptor,
    val isLValue: Boolean = false
) : CodeFragmentParameter by dumb
