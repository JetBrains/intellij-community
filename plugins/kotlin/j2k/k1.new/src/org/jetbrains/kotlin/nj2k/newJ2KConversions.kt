// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.nj2k

import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.j2k.ConverterContext
import org.jetbrains.kotlin.nj2k.conversions.AddConstModifierConversion
import org.jetbrains.kotlin.nj2k.conversions.AddElementsInfoConversion
import org.jetbrains.kotlin.nj2k.conversions.AnnotationClassConversion
import org.jetbrains.kotlin.nj2k.conversions.AnnotationConversion
import org.jetbrains.kotlin.nj2k.conversions.AnyWithStringConcatenationConversion
import org.jetbrains.kotlin.nj2k.conversions.ArrayInitializerConversion
import org.jetbrains.kotlin.nj2k.conversions.ArrayOperationsConversion
import org.jetbrains.kotlin.nj2k.conversions.AssignmentExpressionUnfoldingConversion
import org.jetbrains.kotlin.nj2k.conversions.BlockToRunConversion
import org.jetbrains.kotlin.nj2k.conversions.BoxedTypeOperationsConversion
import org.jetbrains.kotlin.nj2k.conversions.BuiltinMembersConversion
import org.jetbrains.kotlin.nj2k.conversions.ClassMemberConversion
import org.jetbrains.kotlin.nj2k.conversions.ClassToObjectPromotionConversion
import org.jetbrains.kotlin.nj2k.conversions.ConstructorConversion
import org.jetbrains.kotlin.nj2k.conversions.DefaultArgumentsConversion
import org.jetbrains.kotlin.nj2k.conversions.EnumFieldAccessConversion
import org.jetbrains.kotlin.nj2k.conversions.EnumSyntheticValuesMethodConversion
import org.jetbrains.kotlin.nj2k.conversions.EqualsOperatorConversion
import org.jetbrains.kotlin.nj2k.conversions.FilterImportsConversion
import org.jetbrains.kotlin.nj2k.conversions.ForConversion
import org.jetbrains.kotlin.nj2k.conversions.FunctionAsAnonymousObjectToLambdaConversion
import org.jetbrains.kotlin.nj2k.conversions.FunctionalInterfacesConversion
import org.jetbrains.kotlin.nj2k.conversions.ImplicitCastsConversion
import org.jetbrains.kotlin.nj2k.conversions.ImplicitInitializerConversion
import org.jetbrains.kotlin.nj2k.conversions.InnerClassConversion
import org.jetbrains.kotlin.nj2k.conversions.InsertDefaultPrimaryConstructorConversion
import org.jetbrains.kotlin.nj2k.conversions.InterfaceWithFieldConversion
import org.jetbrains.kotlin.nj2k.conversions.InternalDeclarationConversion
import org.jetbrains.kotlin.nj2k.conversions.JavaAnnotationsConversion
import org.jetbrains.kotlin.nj2k.conversions.JavaModifiersConversion
import org.jetbrains.kotlin.nj2k.conversions.JavaStandardMethodsConversion
import org.jetbrains.kotlin.nj2k.conversions.JavaStatementConversion
import org.jetbrains.kotlin.nj2k.conversions.LabeledStatementConversion
import org.jetbrains.kotlin.nj2k.conversions.LiteralConversion
import org.jetbrains.kotlin.nj2k.conversions.MethodReferenceToLambdaConversion
import org.jetbrains.kotlin.nj2k.conversions.ModalityConversion
import org.jetbrains.kotlin.nj2k.conversions.MoveConstructorsAfterFieldsConversion
import org.jetbrains.kotlin.nj2k.conversions.NonCodeElementsConversion
import org.jetbrains.kotlin.nj2k.conversions.NullabilityAnnotationsConversion
import org.jetbrains.kotlin.nj2k.conversions.NullabilityConversion
import org.jetbrains.kotlin.nj2k.conversions.ParameterModificationConversion
import org.jetbrains.kotlin.nj2k.conversions.ParenthesizeBitwiseOperationConversion
import org.jetbrains.kotlin.nj2k.conversions.ParenthesizeMultilineBinaryExpressionConversion
import org.jetbrains.kotlin.nj2k.conversions.PrimaryConstructorDetectConversion
import org.jetbrains.kotlin.nj2k.conversions.PrimitiveTypeCastsConversion
import org.jetbrains.kotlin.nj2k.conversions.RecordClassConversion
import org.jetbrains.kotlin.nj2k.conversions.RemoveRedundantQualifiersForCallsConversion
import org.jetbrains.kotlin.nj2k.conversions.RemoveUnnecessaryParenthesesConversion
import org.jetbrains.kotlin.nj2k.conversions.RemoveWrongOtherModifiersConversion
import org.jetbrains.kotlin.nj2k.conversions.ReturnStatementInLambdaExpressionConversion
import org.jetbrains.kotlin.nj2k.conversions.SimplifyNegatedBinaryExpressionConversion
import org.jetbrains.kotlin.nj2k.conversions.StaticsToCompanionExtractConversion
import org.jetbrains.kotlin.nj2k.conversions.SwitchToWhenConversion
import org.jetbrains.kotlin.nj2k.conversions.TypeMappingConversion
import org.jetbrains.kotlin.nj2k.conversions.YieldStatementConversion
import org.jetbrains.kotlin.nj2k.tree.JKLambdaExpression
import org.jetbrains.kotlin.nj2k.tree.JKParameter
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

@K1Deprecation
@Suppress("DuplicatedCode")
fun getNewJ2KConversions(context: ConverterContext): List<Conversion> = listOf(
    NullabilityConversion(context),
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
    ParameterModificationConversion(context),
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
    SimplifyNegatedBinaryExpressionConversion(context),
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
    RemoveUnnecessaryParenthesesConversion(context),
    AddElementsInfoConversion(context),
    AddConstModifierConversion(context),
    EnumSyntheticValuesMethodConversion(context)
)