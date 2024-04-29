// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine

import com.intellij.openapi.Disposable
import org.jetbrains.kotlin.idea.base.psi.unifier.KotlinPsiRange
import org.jetbrains.kotlin.psi.KtNamedDeclaration

interface IExtractionResult<KotlinType> : Disposable {
    val config: IExtractionGeneratorConfiguration<KotlinType>
    var declaration: KtNamedDeclaration
    val duplicateReplacers: Map<KotlinPsiRange, () -> Unit>
}