// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k

import org.jetbrains.kotlin.nj2k.conversions.*
import org.jetbrains.kotlin.nj2k.tree.JKLambdaExpression
import org.jetbrains.kotlin.nj2k.tree.JKParameter
import org.jetbrains.kotlin.nj2k.tree.JKTreeRoot
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import java.util.*

object ConversionsRunner {
    private fun createConversions(context: NewJ2kConverterContext) = listOf(
        ParenthesizeBinaryExpressionIfNeededConversion(context),
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
        RemoveWrongExtraModifiersForSingleFunctionsConversion(context),
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


    fun doApply(
        trees: List<JKTreeRoot>,
        context: NewJ2kConverterContext,
        updateProgress: (conversionIndex: Int, conversionCount: Int, fileIndex: Int, String) -> Unit
    ) {

        val conversions = createConversions(context)
        for ((conversionIndex, conversion) in conversions.withIndex()) {

            val treeSequence = trees.asSequence().onEachIndexed { index, _ ->
                updateProgress(conversionIndex, conversions.size, index, conversion.description())
            }

            conversion.runConversion(treeSequence, context)
        }
    }

    private fun Conversion.description(): String {
        val conversionName = this::class.simpleName
        val words = conversionName?.let {
            wordRegex.findAll(conversionName).map { it.value.replaceFirstChar { char -> char.lowercase(Locale.US) } }.toList()
        }
        return when {
            conversionName == null -> "Converting..."
            conversionName.endsWith("Conversion") -> "Converting ${words!!.dropLast(1).joinToString(" ")}"
            else -> words!!.joinToString(" ")
        }
    }

    private val wordRegex = """[A-Z][a-z\d]+""".toRegex()
}
