// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.extractFunction

import com.intellij.psi.PsiElement
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.*
import org.jetbrains.kotlin.lexer.KtKeywordToken
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtSimpleNameExpression

data class ExtractableCodeDescriptor(
    val context: KtElement,
    override val extractionData: ExtractionData,
    override val suggestedNames: List<String>,
    override val visibility: KtModifierKeywordToken?,
    override val parameters: List<Parameter>,
    override val receiverParameter: Parameter?,
    override val typeParameters: List<TypeParameter>,
    override val replacementMap: MultiMap<KtSimpleNameExpression, IReplacement<KaType>>,
    override val controlFlow: ControlFlow<KaType>,
    override val returnType: KaType,
    override val modifiers: List<KtKeywordToken> = emptyList(),
    override val optInMarkers: List<FqName> = emptyList(),
    val renderedAnnotations: List<String> = emptyList(),
) : IExtractableCodeDescriptor<KaType> {
    override val name: String get() = suggestedNames.firstOrNull() ?: ""

    override val duplicates: List<DuplicateInfo<KaType>> by lazy { findDuplicates() }

    private val isUnitReturn: Boolean = analyze(context) { returnType.isUnitType }

    override fun isUnitReturnType(): Boolean = isUnitReturn

    override val annotationsText: String
        get() = if (renderedAnnotations.isEmpty()) {
            ""
        } else {
            renderedAnnotations.joinToString(separator = "\n")
        }
}

internal fun getPossibleReturnTypes(cfg: ControlFlow<KaType>): List<KaType> {
    return cfg.possibleReturnTypes
}

data class ExtractableCodeDescriptorWithConflicts(
    override val descriptor: ExtractableCodeDescriptor,
    override val conflicts: MultiMap<PsiElement, String>
) : ExtractableCodeDescriptorWithConflictsResult(), IExtractableCodeDescriptorWithConflicts<KaType>
