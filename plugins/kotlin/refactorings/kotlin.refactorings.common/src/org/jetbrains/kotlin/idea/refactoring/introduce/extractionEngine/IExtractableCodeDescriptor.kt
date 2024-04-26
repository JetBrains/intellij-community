// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine

import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.lexer.KtKeywordToken
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtSimpleNameExpression

/**
 * Interface for describing extractable code.
 */
interface IExtractableCodeDescriptor<KotlinType> {
    val extractionData: IExtractionData
    val suggestedNames: List<String>
    val visibility: KtModifierKeywordToken?
    val parameters: List<IParameter<KotlinType>>
    val receiverParameter: IParameter<KotlinType>?
    val typeParameters: List<TypeParameter>
    val replacementMap: MultiMap<KtSimpleNameExpression, IReplacement<KotlinType>>
    val controlFlow: ControlFlow<KotlinType>
    val returnType: KotlinType
    val modifiers: List<KtKeywordToken>
    val annotationsText: String
    val optInMarkers: List<FqName>

    val name: String get() = suggestedNames.firstOrNull() ?: ""
    fun isUnitReturnType(): Boolean

    val duplicates: List<DuplicateInfo<KotlinType>>
}