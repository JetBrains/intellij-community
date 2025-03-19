// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k.k2

import org.jetbrains.kotlin.j2k.ConverterContext
import org.jetbrains.kotlin.nj2k.Conversion
import org.jetbrains.kotlin.nj2k.conversions.*
import org.jetbrains.kotlin.nj2k.tree.JKLambdaExpression
import org.jetbrains.kotlin.nj2k.tree.JKParameter
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

@Suppress("DuplicatedCode")
internal fun getK2J2KConversions(context: ConverterContext): List<Conversion> = listOf(
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
    AddConstModifierConversion(context),
    EnumSyntheticValuesMethodConversion(context),
    RemoveUnnecessaryParenthesesConversion(context),
)