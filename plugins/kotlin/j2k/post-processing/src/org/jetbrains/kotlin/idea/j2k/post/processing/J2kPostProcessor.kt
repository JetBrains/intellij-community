// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.j2k.post.processing

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.caches.resolve.resolveImportReference
import org.jetbrains.kotlin.idea.codeInsight.intentions.shared.RemoveUnnecessaryParenthesesIntention
import org.jetbrains.kotlin.idea.codeinsight.utils.NegatedBinaryExpressionSimplificationUtils.canBeSimplifiedWithoutChangingSemantics
import org.jetbrains.kotlin.idea.codeinsight.utils.commitAndUnblockDocument
import org.jetbrains.kotlin.idea.codeinsights.impl.base.KotlinInspectionFacade
import org.jetbrains.kotlin.idea.inspections.*
import org.jetbrains.kotlin.idea.inspections.branchedTransformations.IfThenToElvisInspection
import org.jetbrains.kotlin.idea.inspections.branchedTransformations.IfThenToSafeAccessInspection
import org.jetbrains.kotlin.idea.inspections.conventionNameCalls.ReplaceGetOrSetInspection
import org.jetbrains.kotlin.idea.intentions.*
import org.jetbrains.kotlin.idea.intentions.branchedTransformations.intentions.FoldIfToReturnAsymmetricallyIntention
import org.jetbrains.kotlin.idea.intentions.branchedTransformations.intentions.FoldIfToReturnIntention
import org.jetbrains.kotlin.idea.intentions.branchedTransformations.isTrivialStatementBody
import org.jetbrains.kotlin.idea.j2k.post.processing.processings.*
import org.jetbrains.kotlin.idea.quickfix.*
import org.jetbrains.kotlin.idea.util.ImportInsertHelper
import org.jetbrains.kotlin.j2k.ConverterContext
import org.jetbrains.kotlin.j2k.JKPostProcessingTarget
import org.jetbrains.kotlin.j2k.PostProcessor
import org.jetbrains.kotlin.j2k.files
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.lexer.KtTokens.CONST_KEYWORD
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.nj2k.NewJ2kConverterContext
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parents

class NewJ2kPostProcessor : PostProcessor {
    companion object {
        private val LOG = Logger.getInstance("@org.jetbrains.kotlin.idea.j2k.post.processings.NewJ2kPostProcessor")
    }

    override fun insertImport(file: KtFile, fqName: FqName) {
        runUndoTransparentActionInEdt(inWriteAction = true) {
            val descriptors = file.resolveImportReference(fqName)
            descriptors.firstOrNull()?.let { ImportInsertHelper.getInstance(file.project).importDescriptor(file, it) }
        }
    }

    override val phasesCount = processings.size


    override fun doAdditionalProcessing(
        target: JKPostProcessingTarget,
        converterContext: ConverterContext?,
        onPhaseChanged: ((Int, String) -> Unit)?
    ) {
        if (converterContext !is NewJ2kConverterContext) error("Invalid converter context for new J2K")
        for ((i, group) in processings.withIndex()) {
            ProgressManager.checkCanceled()
            onPhaseChanged?.invoke(i, group.description)
            for (processing in group.processings) {
                ProgressManager.checkCanceled()
                try {
                    processing.runProcessingConsideringOptions(target, converterContext)

                    target.files().forEach(::commitFile)
                } catch (e: ProcessCanceledException) {
                    throw e
                } catch (t: Throwable) {
                    target.files().forEach(::commitFile)
                    LOG.error(t)
                }
            }
        }
    }

    private fun GeneralPostProcessing.runProcessingConsideringOptions(
        target: JKPostProcessingTarget,
        converterContext: NewJ2kConverterContext
    ) {

        if (options.disablePostprocessingFormatting) {
            PostprocessReformattingAspect.getInstance(converterContext.project).disablePostprocessFormattingInside {
                runProcessing(target, converterContext)
            }
        } else {
            runProcessing(target, converterContext)
        }
    }

    private fun commitFile(file: KtFile) {
        runUndoTransparentActionInEdt(inWriteAction = true) {
            file.commitAndUnblockDocument()
        }
    }
}

private val errorsFixingDiagnosticBasedPostProcessingGroup =
    DiagnosticBasedPostProcessingGroup(
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
            MissingIteratorExclExclFixFactory,
            Errors.ITERATOR_ON_NULLABLE
        ),
        diagnosticBasedProcessing(
            SmartCastImpossibleExclExclFixFactory,
            Errors.SMARTCAST_IMPOSSIBLE
        ),

        diagnosticBasedProcessing(
            ReplacePrimitiveCastWithNumberConversionFix,
            Errors.CAST_NEVER_SUCCEEDS
        ),
        diagnosticBasedProcessing(
            ChangeCallableReturnTypeFix.ReturnTypeMismatchOnOverrideFactory,
            Errors.RETURN_TYPE_MISMATCH_ON_OVERRIDE
        ),

        diagnosticBasedProcessing(
            RemoveModifierFixBase.createRemoveProjectionFactory(true),
            Errors.REDUNDANT_PROJECTION
        ),
        diagnosticBasedProcessing(
            AddModifierFixFE10.createFactory(KtTokens.OVERRIDE_KEYWORD),
            Errors.VIRTUAL_MEMBER_HIDDEN
        ),
        diagnosticBasedProcessing(
            RemoveModifierFixBase.createRemoveModifierFromListOwnerPsiBasedFactory(KtTokens.OPEN_KEYWORD),
            Errors.NON_FINAL_MEMBER_IN_FINAL_CLASS, Errors.NON_FINAL_MEMBER_IN_OBJECT
        ),
        diagnosticBasedProcessing(
            MakeVisibleFactory,
            Errors.INVISIBLE_MEMBER
        ),
        diagnosticBasedProcessing(
            RemoveModifierFixBase.removeNonRedundantModifier,
            Errors.WRONG_MODIFIER_TARGET
        ),
        diagnosticBasedProcessing(
            ChangeVisibilityOnExposureFactory,
            Errors.EXPOSED_FUNCTION_RETURN_TYPE,
            Errors.EXPOSED_PARAMETER_TYPE,
            Errors.EXPOSED_PROPERTY_TYPE,
            Errors.EXPOSED_PROPERTY_TYPE_IN_CONSTRUCTOR.errorFactory,
            Errors.EXPOSED_PROPERTY_TYPE_IN_CONSTRUCTOR.warningFactory,
            Errors.EXPOSED_RECEIVER_TYPE,
            Errors.EXPOSED_SUPER_CLASS,
            Errors.EXPOSED_SUPER_INTERFACE
        ),
        fixValToVarDiagnosticBasedProcessing,
        fixTypeMismatchDiagnosticBasedProcessing
    )


private val addOrRemoveModifiersProcessingGroup =
    InspectionLikeProcessingGroup(
        runSingleTime = true,
        processings = listOf(
            RemoveRedundantVisibilityModifierProcessing(),
            RemoveRedundantModalityModifierProcessing(),
            inspectionBasedProcessing(AddOperatorModifierInspection(), writeActionNeeded = false),
        )
    )

private val removeRedundantElementsProcessingGroup =
    InspectionLikeProcessingGroup(
        runSingleTime = true,
        processings = listOf(
            RemoveExplicitTypeArgumentsProcessing(),
            RemoveJavaStreamsCollectCallTypeArgumentsProcessing(),
            ExplicitThisInspectionBasedProcessing(),
            RemoveOpenModifierOnTopLevelDeclarationsProcessing(),
        )
    )

private val inspectionLikePostProcessingGroup =
    InspectionLikeProcessingGroup(
        RemoveRedundantOverrideVisibilityProcessing(),
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
        JavaObjectEqualsToEqOperatorProcessing(),
        RemoveExplicitPropertyTypeProcessing(),
        RemoveRedundantNullabilityProcessing(),
        CanBeValInspectionBasedProcessing(),
        inspectionBasedProcessing(FoldInitializerAndIfToElvisInspection(), writeActionNeeded = false),
        inspectionBasedProcessing(JavaMapForEachInspection()),
        intentionBasedProcessing(FoldIfToReturnIntention()) { it.then.isTrivialStatementBody() && it.`else`.isTrivialStatementBody() },
        intentionBasedProcessing(FoldIfToReturnAsymmetricallyIntention()) {
            it.then.isTrivialStatementBody() && (KtPsiUtil.skipTrailingWhitespacesAndComments(
                it
            ) as KtReturnExpression).returnedExpression.isTrivialStatementBody()
        },
        inspectionBasedProcessing(IfThenToSafeAccessInspection(inlineWithPrompt = false), writeActionNeeded = false),
        inspectionBasedProcessing(IfThenToElvisInspection(highlightStatement = true, inlineWithPrompt = false), writeActionNeeded = false),
        inspectionBasedProcessing(KotlinInspectionFacade.instance.simplifyNegatedBinaryExpression) {
            it.canBeSimplifiedWithoutChangingSemantics()
        },
        inspectionBasedProcessing(ReplaceGetOrSetInspection()),
        intentionBasedProcessing(ObjectLiteralToLambdaIntention(), writeActionNeeded = true),
        intentionBasedProcessing(RemoveUnnecessaryParenthesesIntention()),
        intentionBasedProcessing(DestructureIntention(), writeActionNeeded = false),
        inspectionBasedProcessing(SimplifyAssertNotNullInspection()),
        LiftReturnInspectionBasedProcessing(),
        LiftAssignmentInspectionBasedProcessing(),
        intentionBasedProcessing(RemoveEmptyPrimaryConstructorIntention()),
        MayBeConstantInspectionBasedProcessing(),
        RemoveForExpressionLoopParameterTypeProcessing(),
        intentionBasedProcessing(ReplaceMapGetOrDefaultIntention()),
        inspectionBasedProcessing(ReplaceGuardClauseWithFunctionCallInspection()),
        inspectionBasedProcessing(KotlinInspectionFacade.instance.sortModifiers),
        intentionBasedProcessing(ConvertToRawStringTemplateIntention(), additionalChecker = ::shouldConvertToRawString),
        intentionBasedProcessing(IndentRawStringIntention())
    )

private val cleaningUpDiagnosticBasedPostProcessingGroup =
    DiagnosticBasedPostProcessingGroup(
        removeUselessCastDiagnosticBasedProcessing,
        removeUnnecessaryNotNullAssertionDiagnosticBasedProcessing,
        fixValToVarDiagnosticBasedProcessing
    )


private val processings: List<NamedPostProcessingGroup> = listOf(
    NamedPostProcessingGroup(
        KotlinNJ2KServicesBundle.message("processing.step.inferring.types"),
        listOf(
            InspectionLikeProcessingGroup(
                processings = listOf(
                    VarToValProcessing(),
                    CanBeValInspectionBasedProcessing()
                ),
                runSingleTime = true
            ),
            NullabilityInferenceProcessing(),
            MutabilityInferenceProcessing(),
            ClearUnknownLabelsProcessing()
        )
    ),
    NamedPostProcessingGroup(
        KotlinNJ2KServicesBundle.message("processing.step.cleaning.up.code"),
        listOf(
            InspectionLikeProcessingGroup(VarToValProcessing()),
            ConvertGettersAndSettersToPropertyProcessing(),
            InspectionLikeProcessingGroup(
                RemoveExplicitGetterInspectionBasedProcessing(),
                RemoveExplicitSetterInspectionBasedProcessing()
            ),
            MergePropertyWithConstructorParameterProcessing(),
            errorsFixingDiagnosticBasedPostProcessingGroup,
            addOrRemoveModifiersProcessingGroup,
            inspectionLikePostProcessingGroup,
            removeRedundantElementsProcessingGroup,
            cleaningUpDiagnosticBasedPostProcessingGroup
        )
    ),
    NamedPostProcessingGroup(
        KotlinNJ2KServicesBundle.message("processing.step.optimizing.imports.and.formatting.code"),
        listOf(
            ShortenReferenceProcessing(),
            OptimizeImportsProcessing(),
            FormatCodeProcessing()
        )
    )
)

private fun shouldConvertToRawString(element: KtBinaryExpression): Boolean {
    fun KtStringTemplateEntry.isNewline(): Boolean =
        this is KtEscapeStringTemplateEntry && unescapedValue == "\n"

    val middleNewlinesExist = ConvertToStringTemplateIntention.Holder.buildReplacement(element)
        .entries
        .dropLastWhile { it.isNewline() }
        .any { it.isNewline() }

    return middleNewlinesExist && element.parents.none {
        (it as? KtProperty)?.hasModifier(CONST_KEYWORD) == true
    }
}