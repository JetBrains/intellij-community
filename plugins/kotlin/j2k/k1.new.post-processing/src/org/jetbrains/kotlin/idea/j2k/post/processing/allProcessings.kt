// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.j2k.post.processing

import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.codeInsight.intentions.shared.IndentRawStringIntention
import org.jetbrains.kotlin.idea.inspections.FoldInitializerAndIfToElvisInspection
import org.jetbrains.kotlin.idea.inspections.NullChecksToSafeCallInspection
import org.jetbrains.kotlin.idea.inspections.ReplacePutWithAssignmentInspection
import org.jetbrains.kotlin.idea.inspections.branchedTransformations.IfThenToElvisInspection
import org.jetbrains.kotlin.idea.inspections.branchedTransformations.IfThenToSafeAccessInspection
import org.jetbrains.kotlin.idea.inspections.conventionNameCalls.ReplaceGetOrSetInspection
import org.jetbrains.kotlin.idea.intentions.*
import org.jetbrains.kotlin.idea.intentions.branchedTransformations.shouldBeTransformed
import org.jetbrains.kotlin.idea.j2k.post.processing.processings.*
import org.jetbrains.kotlin.idea.quickfix.*
import org.jetbrains.kotlin.idea.quickfix.ChangeCallableReturnTypeFix.ReturnTypeMismatchOnOverrideFactory
import org.jetbrains.kotlin.j2k.InspectionLikeProcessingGroup
import org.jetbrains.kotlin.j2k.NamedPostProcessingGroup
import org.jetbrains.kotlin.j2k.postProcessings.*
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtEscapeStringTemplateEntry
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtStringTemplateEntry
import org.jetbrains.kotlin.psi.psiUtil.parents

private val errorsFixingDiagnosticBasedPostProcessingGroup = DiagnosticBasedPostProcessingGroup(
    diagnosticBasedProcessing(MissingIteratorExclExclFixFactory, Errors.ITERATOR_ON_NULLABLE),
    diagnosticBasedProcessing(SmartCastImpossibleExclExclFixFactory, Errors.SMARTCAST_IMPOSSIBLE),
    diagnosticBasedProcessing(ReturnTypeMismatchOnOverrideFactory, Errors.RETURN_TYPE_MISMATCH_ON_OVERRIDE),
    diagnosticBasedProcessing(AddModifierFixFE10.createFactory(KtTokens.OVERRIDE_KEYWORD), Errors.VIRTUAL_MEMBER_HIDDEN),
    invisibleMemberDiagnosticBasedProcessing(MakeVisibleFactory, Errors.INVISIBLE_MEMBER),

    diagnosticBasedProcessing(
        UnsafeCallExclExclFixFactory,
        Errors.UNSAFE_CALL,
        Errors.UNSAFE_INFIX_CALL,
        Errors.UNSAFE_OPERATOR_CALL
    ),
    exposedVisibilityDiagnosticBasedProcessing(
        ChangeVisibilityOnExposureFactory,
        Errors.EXPOSED_FUNCTION_RETURN_TYPE,
        Errors.EXPOSED_PARAMETER_TYPE,
        Errors.EXPOSED_PROPERTY_TYPE,
        Errors.EXPOSED_PROPERTY_TYPE_IN_CONSTRUCTOR.errorFactory,
        Errors.EXPOSED_PROPERTY_TYPE_IN_CONSTRUCTOR.warningFactory,
        Errors.EXPOSED_RECEIVER_TYPE,
        Errors.EXPOSED_SUPER_CLASS,
        Errors.EXPOSED_SUPER_INTERFACE,
        Errors.EXPOSED_TYPE_PARAMETER_BOUND
    ),
    diagnosticBasedProcessing(
        ConvertToIsArrayOfCallFixFactory,
        Errors.CANNOT_CHECK_FOR_ERASED,
    ),
    fixTypeMismatchDiagnosticBasedProcessing
)

private val addOrRemoveModifiersProcessingGroup = InspectionLikeProcessingGroup(
    runSingleTime = true,
    processings = listOf(
        // This is left for copy-paste conversion.
        // On regular conversion, redundant modifiers are removed during JK tree processing.
        RemoveRedundantVisibilityModifierProcessing(),
    )
)

private val removeRedundantElementsProcessingGroup = InspectionLikeProcessingGroup(
    runSingleTime = true,
    processings = listOf(
        RemoveExplicitTypeArgumentsProcessing(),
        RemoveJavaStreamsCollectCallTypeArgumentsProcessing(),
        ExplicitThisInspectionBasedProcessing()
    )
)

private val inspectionLikePostProcessingGroup = InspectionLikeProcessingGroup(
    MoveLambdaOutsideParenthesesProcessing(),
    intentionBasedProcessing(ConvertToStringTemplateIntention(), writeActionNeeded = false) {
        ConvertToStringTemplateIntention.Holder.shouldSuggestToConvert(it)
    },
    intentionBasedProcessing(UsePropertyAccessSyntaxIntention(), writeActionNeeded = false),
    UninitializedVariableReferenceFromInitializerToThisReferenceProcessing(),
    UnresolvedVariableReferenceFromInitializerToThisReferenceProcessing(),
    RemoveRedundantSamAdaptersProcessing(),
    RemoveRedundantCastToNullableProcessing(),
    inspectionBasedProcessing(ReplacePutWithAssignmentInspection()),
    RemoveExplicitPropertyTypeProcessing(),
    RemoveRedundantNullabilityProcessing(),
    inspectionBasedProcessing(FoldInitializerAndIfToElvisInspection(), writeActionNeeded = false),
    inspectionBasedProcessing(IfThenToSafeAccessInspection(inlineWithPrompt = false), writeActionNeeded = false) {
        it.shouldBeTransformed()
    },
    inspectionBasedProcessing(IfThenToElvisInspection(highlightStatement = true, inlineWithPrompt = false), writeActionNeeded = false) {
        it.shouldBeTransformed()
    },
    // ReplaceGetOrSetInspection should always be applied, because as a side effect
    // it fixes red code of the form `array.get(0) = 42`
    inspectionBasedProcessing(ReplaceGetOrSetInspection(), checkInspectionIsEnabled = false),
    intentionBasedProcessing(ObjectLiteralToLambdaIntention(), writeActionNeeded = true),
    DestructureForLoopParameterProcessing(),
    LiftReturnInspectionBasedProcessing(),
    LiftAssignmentInspectionBasedProcessing(),
    MayBeConstantInspectionBasedProcessing(),
    RemoveForExpressionLoopParameterTypeProcessing(),
    intentionBasedProcessing(ConvertToRawStringTemplateIntention(), additionalChecker = ::shouldConvertToRawString),
    intentionBasedProcessing(IndentRawStringIntention()),
    intentionBasedProcessing(JoinDeclarationAndAssignmentIntention()),
    inspectionBasedProcessing(NullChecksToSafeCallInspection())
)

private val cleaningUpDiagnosticBasedPostProcessingGroup = DiagnosticBasedPostProcessingGroup(
    removeUselessCastDiagnosticBasedProcessing,
    removeUnnecessaryNotNullAssertionDiagnosticBasedProcessing,
    fixValToVarDiagnosticBasedProcessing
)

private val inferringTypesPostProcessingGroup = NamedPostProcessingGroup(
    KotlinNJ2KServicesBundle.message("processing.step.inferring.types"),
    listOf(
        InspectionLikeProcessingGroup(
            processings = listOf(
                VarToValProcessing(),
                LocalVarToValInspectionBasedProcessing()
            ),
            runSingleTime = true
        ),
        NullabilityInferenceProcessing(),
        MutabilityInferenceProcessing(),
        ClearUnknownInferenceLabelsProcessing()
    )
)

private val cleaningUpCodePostProcessingGroup = NamedPostProcessingGroup(
    KotlinNJ2KServicesBundle.message("processing.step.cleaning.up.code"),
    listOf(
        DiagnosticBasedPostProcessingGroup(
            // We need to remove the redundant projection before `ConvertGettersAndSettersToPropertyProcessing`,
            // so that the property and accessor types wouldn't differ in projections.
            diagnosticBasedProcessing(RemoveModifierFixBase.createRemoveProjectionFactory(isRedundant = true), Errors.REDUNDANT_PROJECTION),
        ),
        K1ConvertGettersAndSettersToPropertyProcessing(),
        MergePropertyWithConstructorParameterProcessing(),
        errorsFixingDiagnosticBasedPostProcessingGroup,
        addOrRemoveModifiersProcessingGroup,
        inspectionLikePostProcessingGroup,
        removeRedundantElementsProcessingGroup,
        ClearExplicitLabelsProcessing(),
        cleaningUpDiagnosticBasedPostProcessingGroup,
    )
)

private val optimizingImportsAndFormattingCodePostProcessingGroup = NamedPostProcessingGroup(
    KotlinNJ2KServicesBundle.message("processing.step.optimizing.imports.and.formatting.code"),
    listOf(
        ShortenReferenceProcessing(),
        OptimizeImportsProcessing(),
        RemoveRedundantEmptyLinesProcessing(),
        FormatCodeProcessing()
    )
)

internal val allProcessings: List<NamedPostProcessingGroup> = listOf(
    inferringTypesPostProcessingGroup,
    cleaningUpCodePostProcessingGroup,
    optimizingImportsAndFormattingCodePostProcessingGroup
)

private fun shouldConvertToRawString(element: KtBinaryExpression): Boolean {
    fun KtStringTemplateEntry.isNewline(): Boolean =
        this is KtEscapeStringTemplateEntry && unescapedValue == "\n"

    val middleNewlinesExist = ConvertToStringTemplateIntention.Holder.buildReplacement(element)
        .entries
        .dropLastWhile { it.isNewline() }
        .any { it.isNewline() }

    return middleNewlinesExist && element.parents.none {
        (it as? KtProperty)?.hasModifier(KtTokens.CONST_KEYWORD) == true
    }
}