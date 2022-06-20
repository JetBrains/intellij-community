// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.j2k.post.processing

import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.codeInsight.intentions.shared.IndentRawStringIntention
import org.jetbrains.kotlin.idea.codeInsight.intentions.shared.RemoveUnnecessaryParenthesesIntention
import org.jetbrains.kotlin.idea.codeinsight.utils.NegatedBinaryExpressionSimplificationUtils.canBeSimplifiedWithoutChangingSemantics
import org.jetbrains.kotlin.idea.codeinsights.impl.base.KotlinInspectionFacade
import org.jetbrains.kotlin.idea.inspections.*
import org.jetbrains.kotlin.idea.inspections.branchedTransformations.IfThenToElvisInspection
import org.jetbrains.kotlin.idea.inspections.branchedTransformations.IfThenToSafeAccessInspection
import org.jetbrains.kotlin.idea.inspections.conventionNameCalls.ReplaceGetOrSetInspection
import org.jetbrains.kotlin.idea.intentions.*
import org.jetbrains.kotlin.idea.intentions.branchedTransformations.shouldBeTransformed
import org.jetbrains.kotlin.idea.j2k.post.processing.processings.*
import org.jetbrains.kotlin.idea.quickfix.*
import org.jetbrains.kotlin.idea.quickfix.ChangeCallableReturnTypeFix.ReturnTypeMismatchOnOverrideFactory
import org.jetbrains.kotlin.idea.quickfix.ChangeVisibilityFix.SetExplicitVisibilityFactory
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parents

private val errorsFixingDiagnosticBasedPostProcessingGroup = DiagnosticBasedPostProcessingGroup(
    diagnosticBasedProcessing(MissingIteratorExclExclFixFactory, Errors.ITERATOR_ON_NULLABLE),
    diagnosticBasedProcessing(SmartCastImpossibleExclExclFixFactory, Errors.SMARTCAST_IMPOSSIBLE),
    diagnosticBasedProcessing(ReplacePrimitiveCastWithNumberConversionFix, Errors.CAST_NEVER_SUCCEEDS),
    diagnosticBasedProcessing(ReturnTypeMismatchOnOverrideFactory, Errors.RETURN_TYPE_MISMATCH_ON_OVERRIDE),
    diagnosticBasedProcessing(AddModifierFixFE10.createFactory(KtTokens.OVERRIDE_KEYWORD), Errors.VIRTUAL_MEMBER_HIDDEN),
    invisibleMemberDiagnosticBasedProcessing(MakeVisibleFactory, Errors.INVISIBLE_MEMBER),
    diagnosticBasedProcessing(RemoveModifierFixBase.removeNonRedundantModifier, Errors.WRONG_MODIFIER_TARGET),

    diagnosticBasedProcessing(Errors.REDUNDANT_OPEN_IN_INTERFACE) { element: KtModifierListOwner, _ ->
        element.removeModifier(KtTokens.OPEN_KEYWORD)
    },
    diagnosticBasedProcessing(Errors.PLATFORM_CLASS_MAPPED_TO_KOTLIN) { element: KtDotQualifiedExpression, _ ->
        val parent = element.parent as? KtImportDirective ?: return@diagnosticBasedProcessing
        parent.delete()
    },
    diagnosticBasedProcessing(
        UnsafeCallExclExclFixFactory,
        Errors.UNSAFE_CALL,
        Errors.UNSAFE_INFIX_CALL,
        Errors.UNSAFE_OPERATOR_CALL
    ),
    diagnosticBasedProcessing(
        RemoveModifierFixBase.createRemoveModifierFromListOwnerPsiBasedFactory(KtTokens.OPEN_KEYWORD),
        Errors.NON_FINAL_MEMBER_IN_FINAL_CLASS, Errors.NON_FINAL_MEMBER_IN_OBJECT
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
        SetExplicitVisibilityFactory,
        Errors.NO_EXPLICIT_VISIBILITY_IN_API_MODE,
        Errors.NO_EXPLICIT_VISIBILITY_IN_API_MODE_WARNING,
    ),
    diagnosticBasedProcessing(
        ConvertToIsArrayOfCallFix,
        Errors.CANNOT_CHECK_FOR_ERASED,
    ),
    fixTypeMismatchDiagnosticBasedProcessing
)

private val addOrRemoveModifiersProcessingGroup = InspectionLikeProcessingGroup(
    runSingleTime = true,
    processings = listOf(
        RemoveRedundantVisibilityModifierProcessing(),
        RemoveRedundantModalityModifierProcessing(),
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
    ReplaceGetterBodyWithSingleReturnStatementWithExpressionBody(),
    RemoveExplicitPropertyTypeProcessing(),
    RemoveRedundantNullabilityProcessing(),
    inspectionBasedProcessing(FoldInitializerAndIfToElvisInspection(), writeActionNeeded = false),
    inspectionBasedProcessing(JavaMapForEachInspection()),
    inspectionBasedProcessing(IfThenToSafeAccessInspection(inlineWithPrompt = false), writeActionNeeded = false) {
        it.shouldBeTransformed()
    },
    inspectionBasedProcessing(IfThenToElvisInspection(highlightStatement = true, inlineWithPrompt = false), writeActionNeeded = false) {
        it.shouldBeTransformed()
    },
    inspectionBasedProcessing(KotlinInspectionFacade.instance.simplifyNegatedBinaryExpression) {
        it.canBeSimplifiedWithoutChangingSemantics()
    },
    inspectionBasedProcessing(ReplaceGetOrSetInspection()),
    intentionBasedProcessing(ObjectLiteralToLambdaIntention(), writeActionNeeded = true),
    intentionBasedProcessing(RemoveUnnecessaryParenthesesIntention()) {
        // skip parentheses that were originally present in Java code
        it.getExplicitLabelComment() == null
    },
    DestructureForLoopParameterProcessing(),
    inspectionBasedProcessing(SimplifyAssertNotNullInspection()),
    LiftReturnInspectionBasedProcessing(),
    LiftAssignmentInspectionBasedProcessing(),
    intentionBasedProcessing(RemoveEmptyPrimaryConstructorIntention()),
    MayBeConstantInspectionBasedProcessing(),
    inspectionBasedProcessing(ReplaceGuardClauseWithFunctionCallInspection()),
    inspectionBasedProcessing(KotlinInspectionFacade.instance.sortModifiers),
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
                PrivateVarToValProcessing(),
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
        ConvertGettersAndSettersToPropertyProcessing(),
        InspectionLikeProcessingGroup(RemoveExplicitAccessorInspectionBasedProcessing()),
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