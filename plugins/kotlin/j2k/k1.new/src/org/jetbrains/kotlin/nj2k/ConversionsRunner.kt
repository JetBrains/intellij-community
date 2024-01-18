// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.nj2k.conversions.*
import org.jetbrains.kotlin.nj2k.tree.JKLambdaExpression
import org.jetbrains.kotlin.nj2k.tree.JKParameter
import org.jetbrains.kotlin.nj2k.tree.JKTreeRoot
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

internal object ConversionsRunner {
    private fun createConversions(context: NewJ2kConverterContext) = listOf(
        ParenthesizeBitwiseOperationConversion(context),
        ParenthesizeMultilineBinaryExpressionConversion(context),
        NonCodeElementsConversion(context),
        JavaModifiersConversion(context),
        JavaAnnotationsConversion(context),
        AnnotationClassConversion(context),
        AnnotationConversion(context),
        ModalityConversion(context),
        FunctionAsAnonymousObjectToLambdaConversion(context),
        ReturnStatementInLambdaExpressionConversion(context),
        BoxedTypeOperationsConversion(context),
        AnyWithStringConcatenationConversion(context),
        AssignmentExpressionUnfoldingConversion(context),
        ArrayInitializerConversion(context),
        JavaStatementConversion(context),
        EnumFieldAccessConversion(context),
        NullabilityAnnotationsConversion(context),
        DefaultArgumentsConversion(context),
        ConstructorConversion(context),
        MoveConstructorsAfterFieldsConversion(context),
        ImplicitInitializerConversion(context),
        ParameterModificationInMethodCallsConversion(context),
        BlockToRunConversion(context),
        RecordClassConversion(context),
        PrimaryConstructorDetectConversion(context),
        InsertDefaultPrimaryConstructorConversion(context),
        ClassMemberConversion(context),
        JavaStandardMethodsConversion(context),
        SwitchToWhenConversion(context),
        YieldStatementConversion(context),
        ForConversion(context),
        LabeledStatementConversion(context),
        ArrayOperationsConversion(context),
        EqualsOperatorConversion(context),
        TypeMappingConversion(context),
        InternalDeclarationConversion(context),
        InnerClassConversion(context),
        StaticsToCompanionExtractConversion(context),
        InterfaceWithFieldConversion(context),
        ClassToObjectPromotionConversion(context),
        RemoveWrongOtherModifiersConversion(context),
        MethodReferenceToLambdaConversion(context),
        TypeMappingConversion(context) { typeElement ->
            typeElement.parent.safeAs<JKParameter>()?.parent is JKLambdaExpression
        },
        BuiltinMembersConversion(context),
        ImplicitCastsConversion(context),
        PrimitiveTypeCastsConversion(context),
        LiteralConversion(context),
        RemoveRedundantQualifiersForCallsConversion(context),
        FunctionalInterfacesConversion(context),
        FilterImportsConversion(context),
        AddElementsInfoConversion(context),
        EnumSyntheticValuesMethodConversion(context)
    )

    context(KtAnalysisSession)
    fun doApply(
        trees: List<JKTreeRoot>,
        context: NewJ2kConverterContext,
        updateProgress: (conversionIndex: Int, conversionCount: Int, fileIndex: Int, description: String) -> Unit
    ) {
        val conversions = createConversions(context)
        val applyingConversionsMessage: String = KotlinNJ2KBundle.message("j2k.applying.conversions")

        for ((conversionIndex, conversion) in conversions.withIndex()) {
            val treeSequence = trees.asSequence().onEachIndexed { index, _ ->
                updateProgress(conversionIndex, conversions.size, index, applyingConversionsMessage)
            }

            conversion.runForEach(treeSequence, context)
        }
    }
}
