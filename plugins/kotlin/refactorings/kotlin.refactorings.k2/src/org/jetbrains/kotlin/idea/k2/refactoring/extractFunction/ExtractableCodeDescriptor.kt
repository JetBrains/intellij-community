// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.extractFunction

import com.intellij.psi.PsiElement
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotated
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotation
import org.jetbrains.kotlin.analysis.api.renderer.base.annotations.KaRendererAnnotationsFilter
import org.jetbrains.kotlin.analysis.api.renderer.declarations.impl.KaDeclarationRendererForSource
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.utils.printer.PrettyPrinter
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.ControlFlow
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.DuplicateInfo
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.ExtractableCodeDescriptorWithConflictsResult
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.IExtractableCodeDescriptor
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.IExtractableCodeDescriptorWithConflicts
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.IReplacement
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.TypeParameter
import org.jetbrains.kotlin.lexer.KtKeywordToken
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

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
    val annotationClassIds: Set<ClassId> = emptySet()
) : IExtractableCodeDescriptor<KaType> {
    override val name: String get() = suggestedNames.firstOrNull() ?: ""

    override val duplicates: List<DuplicateInfo<KaType>> by lazy { findDuplicates() }

    private val isUnitReturn: Boolean = analyze(context) { returnType.isUnit }

    override fun isUnitReturnType(): Boolean = isUnitReturn

    @OptIn(KaExperimentalApi::class)
    override val annotationsText: String
        get() {
            if (annotationClassIds.isEmpty()) return ""
            val container = extractionData.commonParent.getStrictParentOfType<KtNamedFunction>() ?: return ""
            return analyze(container) {
                val filteredRenderer = KaDeclarationRendererForSource.WITH_QUALIFIED_NAMES.annotationRenderer.with {
                    annotationFilter = annotationFilter.and(object : KaRendererAnnotationsFilter {
                        override fun filter(
                            analysisSession: KaSession,
                            annotation: KaAnnotation,
                            owner: KaAnnotated
                        ): Boolean {
                            return annotation.classId in annotationClassIds
                        }
                    })

                }
                val printer = PrettyPrinter()
                filteredRenderer.renderAnnotations(useSiteSession, container.symbol, printer)
                printer.toString() + "\n"
            }
        }
}

internal fun getPossibleReturnTypes(cfg: ControlFlow<KaType>): List<KaType> {
    return cfg.possibleReturnTypes
}

data class ExtractableCodeDescriptorWithConflicts(
    override val descriptor: ExtractableCodeDescriptor,
    override val conflicts: MultiMap<PsiElement, String>
) : ExtractableCodeDescriptorWithConflictsResult(), IExtractableCodeDescriptorWithConflicts<KaType>
