// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.extractFunction

import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.idea.base.psi.unifier.KotlinPsiRange
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.IExtractionResult
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.unmarkReferencesInside
import org.jetbrains.kotlin.psi.KtNamedDeclaration

data class ExtractionResult(
    override val config: ExtractionGeneratorConfiguration,
    override var declaration: KtNamedDeclaration,
    override val duplicateReplacers: Map<KotlinPsiRange, () -> Unit>
) : IExtractionResult<KtType> {
    override fun dispose() = unmarkReferencesInside(declaration)
}