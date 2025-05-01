// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.fir.testGenerator.codeinsight

import org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AbstractHighLevelQuickFixMultiFileTest
import org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AbstractHighLevelQuickFixMultiModuleTest
import org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AbstractHighLevelQuickFixTest
import org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AbstractHighLevelWithPostponedQuickFixMultiModuleTest
import org.jetbrains.kotlin.testGenerator.model.*
import org.jetbrains.kotlin.testGenerator.model.GroupCategory.QUICKFIXES
import org.jetbrains.kotlin.testGenerator.model.Patterns.DIRECTORY
import org.jetbrains.kotlin.testGenerator.model.Patterns.KT_WITHOUT_DOTS

internal fun MutableTWorkspace.generateK2FixTests() {
    val idea = "idea/tests/testData/"
    testGroup("code-insight/fixes-k2/tests", category = QUICKFIXES, testDataPath = "../../..") {
        testClass<AbstractHighLevelQuickFixTest> {
            val pattern = Patterns.forRegex("^([\\w\\-_]+)\\.kt$")
            model("$idea/quickfix/abstract", pattern = pattern)
            model("$idea/quickfix/addExclExclCall", pattern = pattern)
            model("$idea/quickfix/addInitializer", pattern = pattern)
            model("$idea/quickfix/addPropertyAccessors", pattern = pattern)
            model("$idea/quickfix/addValVar", pattern = pattern)
            model("$idea/quickfix/autoImports", pattern = KT_WITHOUT_DOTS, isRecursive = true)
            model("$idea/quickfix/changeToMutableCollection", pattern = pattern, isRecursive = false)
            model("$idea/quickfix/checkArguments", pattern = pattern, isRecursive = false)
            model("$idea/quickfix/conflictingImports", pattern = pattern)
            model("$idea/quickfix/expressions", pattern = pattern)
            model("$idea/quickfix/lateinit", pattern = pattern)
            model("$idea/quickfix/localVariableWithTypeParameters", pattern = pattern)
            model("$idea/quickfix/modifiers", pattern = pattern, isRecursive = false)
            model("$idea/quickfix/modifiers/addOpenToClassDeclaration", pattern = pattern)
            model("$idea/quickfix/modifiers/suspend", pattern = pattern)
            model("$idea/quickfix/nullables", pattern = pattern)
            model("$idea/quickfix/override", pattern = pattern, isRecursive = false)
            model("$idea/quickfix/override/nothingToOverride", pattern = pattern, isRecursive = false)
            model("$idea/quickfix/override/overrideDeprecation", pattern = pattern)
            model("$idea/quickfix/override/typeMismatchOnOverride", pattern = pattern, isRecursive = false)
            model("$idea/quickfix/removeRedundantSpreadOperator", pattern = pattern)
            model("$idea/quickfix/replaceAndWithWhenGuard", pattern = pattern)
            model("$idea/quickfix/replaceInfixOrOperatorCall", pattern = pattern)
            model("$idea/quickfix/replaceWithArrayCallInAnnotation", pattern = pattern)
            model("$idea/quickfix/replaceWithDotCall", pattern = pattern)
            model("$idea/quickfix/replaceWithSafeCall", pattern = pattern)
            model("$idea/quickfix/specifyVisibilityInExplicitApiMode", pattern = pattern)
            model("$idea/quickfix/supercalls", pattern = pattern)
            model("$idea/quickfix/surroundWithArrayOfForNamedArgumentsToVarargs", pattern = pattern)
            model("$idea/quickfix/variables/changeMutability", pattern = pattern, isRecursive = false)
            model("$idea/quickfix/variables/changeMutability/canBeVal", pattern = pattern)
            model("$idea/quickfix/variables/removeValVarFromParameter", pattern = pattern)
            model("$idea/quickfix/when", pattern = pattern)
            model("$idea/quickfix/wrapWithSafeLetCall", pattern = pattern)
            model("$idea/quickfix/typeAddition", pattern = pattern)
            model("$idea/quickfix/typeMismatch/casts", pattern = pattern)
            model("$idea/quickfix/typeMismatch/componentFunctionReturnTypeMismatch", pattern = pattern)
            model("$idea/quickfix/typeMismatch/convertKClassToJavaClass", pattern = pattern)
            model("$idea/quickfix/typeMismatch/incompatibleTypes", pattern = pattern)
            model("$idea/quickfix/typeMismatch/letImplementInterface", pattern = pattern)
            model("$idea/quickfix/typeMismatch/numberConversion", pattern = pattern)
            model("$idea/quickfix/typeMismatch/parameterTypeMismatch", pattern = pattern)
            model("$idea/quickfix/typeMismatch/roundNumber", pattern = pattern)
            model("$idea/quickfix/typeMismatch/surroundWithLambda", pattern = pattern)
            model("$idea/quickfix/typeMismatch/typeMismatchOnReturnedExpression", pattern = pattern)
            model("$idea/quickfix/typeMismatch/wrongPrimitive", pattern = pattern)
            model("$idea/quickfix/typeMismatch/definitelyNonNullableTypes", pattern = pattern)
            model("$idea/quickfix/typeMismatch/fixOverloadedOperator", pattern = pattern)
            model("$idea/quickfix/typeMismatch", isRecursive = false, pattern = pattern)
            model("$idea/quickfix/toString", pattern = pattern)
            model("$idea/quickfix/specifySuperType", pattern = pattern)
            model("$idea/quickfix/convertToBlockBody", pattern = pattern)
            model("$idea/quickfix/supertypeInitialization", pattern = pattern)
            model("$idea/quickfix/dataClassConstructorVsCopyVisibility", pattern = pattern)
            model("$idea/quickfix/addAnnotationTarget", pattern = pattern, isIgnored = true)
            model("$idea/quickfix/addAnnotationUseSiteTarget", pattern = pattern)
            model("$idea/quickfix/addAnnotationUseSiteTargetForConstructorParameter", pattern = pattern)
            model("$idea/quickfix/addConstructorParameter", pattern = pattern, isIgnored = true)
            model("$idea/quickfix/addConstructorParameterFromSuperTypeCall", pattern = pattern)
            model("$idea/quickfix/addConversionCall", pattern = pattern)
            model("$idea/quickfix/addCrossinline", pattern = pattern)
            model("$idea/quickfix/addDataModifier", pattern = pattern)
            model("$idea/quickfix/addDefaultConstructor", pattern = pattern)
            model("$idea/quickfix/addElseBranchToIf", pattern = pattern)
            model("$idea/quickfix/addEmptyArgumentList", pattern = pattern)
            model("$idea/quickfix/addEqEqTrue", pattern = pattern)
            model("$idea/quickfix/addFunModifier", pattern = pattern)
            model("$idea/quickfix/addGenericUpperBound", pattern = pattern)
            model("$idea/quickfix/addInline", pattern = pattern)
            model("$idea/quickfix/addInlineToReifiedFunctionFix", pattern = pattern)
            model("$idea/quickfix/addIsToWhenCondition", pattern = pattern)
            model("$idea/quickfix/addJvmInline", pattern = pattern)
            model("$idea/quickfix/addJvmStaticAnnotation", pattern = pattern)
            model("$idea/quickfix/addNewLineAfterAnnotations", pattern = pattern, isIgnored = true)
            model("$idea/quickfix/addNoinline", pattern = pattern)
            model("$idea/quickfix/addReifiedToTypeParameterOfFunctionFix", pattern = pattern)
            model("$idea/quickfix/addReturnExpression", pattern = pattern)
            model("$idea/quickfix/addReturnToLastExpressionInFunction", pattern = pattern)
            model("$idea/quickfix/addReturnToUnusedLastExpressionInFunction", pattern = pattern, isIgnored = true)
            model("$idea/quickfix/addRunBeforeLambda", pattern = pattern)
            model("$idea/quickfix/addSemicolonBeforeLambdaExpression", pattern = pattern)
            model("$idea/quickfix/addSpreadOperatorForArrayAsVarargAfterSam", pattern = pattern, isIgnored = true)
            model("$idea/quickfix/addStarProjections", pattern = pattern)
            model("$idea/quickfix/addSuspend", pattern = pattern, isIgnored = true)
            model("$idea/quickfix/addTypeAnnotationToValueParameter", pattern = pattern)
            model("$idea/quickfix/addUnsafeVarianceAnnotation", pattern = pattern)
            model("$idea/quickfix/addVarianceModifier", pattern = pattern, isIgnored = true)
            model("$idea/quickfix/assignToProperty", pattern = pattern)
            model("$idea/quickfix/callFromPublicInline", pattern = pattern)
            model("$idea/quickfix/canBeParameter", pattern = pattern)
            model("$idea/quickfix/canBePrimaryConstructorProperty", pattern = pattern)
            model("$idea/quickfix/castDueToProgressionResolveChange", pattern = pattern, isIgnored = true)
            model("$idea/quickfix/changeObjectToClass", pattern = pattern)
            model("$idea/quickfix/changeSuperTypeListEntryTypeArgument", pattern = pattern, isIgnored = true)
            model("$idea/quickfix/changeToLabeledReturn", pattern = pattern)
            model("$idea/quickfix/changeToUseSpreadOperator", pattern = pattern)
            model("$idea/quickfix/checkArguments/addNameToArgument", pattern = pattern)
            model("$idea/quickfix/compilerError", pattern = pattern, isIgnored = true)
            model("$idea/quickfix/convertCollectionLiteralToIntArrayOf", pattern = pattern)
            model("$idea/quickfix/convertIllegalEscapeToUnicodeEscape", pattern = pattern)
            model("$idea/quickfix/convertJavaInterfaceToClass", pattern = pattern, isIgnored = true)
            model("$idea/quickfix/convertLateinitPropertyToNotNullDelegate", pattern = pattern)
            model("$idea/quickfix/convertPropertyInitializerToGetter", pattern = pattern)
            model("$idea/quickfix/convertToAnonymousObject", pattern = pattern, isIgnored = true)
            model("$idea/quickfix/convertToIsArrayOfCall", pattern = pattern)
            model("$idea/quickfix/createFromUsage/createClass", pattern = pattern, excludedDirectories = listOf("importDirective/kt21515", "callExpression/typeArguments"))
            model("$idea/quickfix/createFromUsage/createVariable", pattern = pattern)
            model("$idea/quickfix/createFromUsage/createFunction/call", pattern = pattern,
                  excludedDirectories = listOf("extensionByExtensionReceiver", "typeArguments"))
            // binaryOperations, callableReferences, component, delegateAccessors, get, hasNext, invoke, iterator, next, set, unaryOperations
            model("$idea/quickfix/createLabel", pattern = pattern)
            model("$idea/quickfix/declarationCantBeInlined", pattern = pattern)
            model("$idea/quickfix/declaringJavaClass", pattern = pattern, isIgnored = true)
            model("$idea/quickfix/decreaseVisibility", pattern = pattern)
            model("$idea/quickfix/deprecatedJavaAnnotation", pattern = pattern, isIgnored = true)
            model("$idea/quickfix/deprecatedSymbolUsage", pattern = pattern, isIgnored = false)
            model("$idea/quickfix/equalityNotApplicable", pattern = pattern)
            model("$idea/quickfix/final", pattern = pattern)
            model("$idea/quickfix/foldTryCatch", pattern = pattern)
            model("$idea/quickfix/functionWithLambdaExpressionBody", pattern = pattern, isIgnored = true)
            model("$idea/quickfix/implement", pattern = pattern)
            model("$idea/quickfix/importAlias", pattern = pattern, isIgnored = true)
            model("$idea/quickfix/increaseVisibility", pattern = pattern)
            model("$idea/quickfix/initializeWithConstructorParameter", pattern = pattern)
            model("$idea/quickfix/inlineClass", pattern = pattern)
            model("$idea/quickfix/inlineTypeParameterFix", pattern = pattern)
            model("$idea/quickfix/insertDelegationCall", pattern = pattern)
            model("$idea/quickfix/isEnumEntry", pattern = pattern)
            model("$idea/quickfix/javaClassOnCompanion", pattern = pattern)
            model("$idea/quickfix/kdocMissingDocumentation", pattern = pattern, isIgnored = true)
            model("$idea/quickfix/leakingThis", pattern = pattern, isIgnored = true)
            model("$idea/quickfix/libraries", pattern = pattern)
            model("$idea/quickfix/makeConstructorParameterProperty", pattern = pattern)
            model("$idea/quickfix/makePrivateAndOverrideMember", pattern = pattern, isIgnored = true)
            model("$idea/quickfix/makeTypeParameterReified", pattern = pattern)
            model("$idea/quickfix/makeUpperBoundNonNullable", pattern = pattern, isIgnored = true)
            model("$idea/quickfix/memberVisibilityCanBePrivate", pattern = pattern)
            model("$idea/quickfix/migration/commasInWhenWithoutArgument", pattern = pattern)
            model("$idea/quickfix/migration/missingConstructorKeyword", pattern = pattern)
            model("$idea/quickfix/migration/removeNameFromFunctionExpression", pattern = pattern)
            model("$idea/quickfix/migration/typeParameterList", pattern = pattern)
            model("$idea/quickfix/missingConstructorBrackets", pattern = pattern)
            model("$idea/quickfix/moveMemberToCompanionObject", pattern = pattern, isIgnored = true)
            model("$idea/quickfix/moveReceiverAnnotation", pattern = pattern)
            model("$idea/quickfix/moveToConstructorParameters", pattern = pattern)
            model("$idea/quickfix/moveToSealedParent", pattern = pattern, isIgnored = true)
            model("$idea/quickfix/moveTypeAliasToTopLevel", pattern = pattern)
            model("$idea/quickfix/obsoleteKotlinJsPackages", pattern = pattern, isIgnored = true)
            model("$idea/quickfix/optimizeImports", pattern = pattern, isIgnored = true)
            model("$idea/quickfix/platformClasses", pattern = pattern, isIgnored = true)
            model("$idea/quickfix/platformTypesInspection", pattern = pattern, isIgnored = true)
            model("$idea/quickfix/primitiveCastToConversion", pattern = pattern)
            model("$idea/quickfix/properties", pattern = pattern)
            model("$idea/quickfix/protectedInFinal", pattern = pattern)
            model("$idea/quickfix/redundantConst", pattern = pattern)
            model("$idea/quickfix/redundantFun", pattern = pattern)
            model("$idea/quickfix/redundantInline", pattern = pattern)
            model("$idea/quickfix/redundantLateinit", pattern = pattern)
            model("$idea/quickfix/redundantModalityModifier", pattern = pattern)
            model("$idea/quickfix/redundantSuspend", pattern = pattern, isIgnored = true)
            model("$idea/quickfix/runBlockingInSuspendFunction", pattern = pattern)
            model("$idea/quickfix/redundantVisibilityModifier", pattern = pattern)
            model("$idea/quickfix/removeAnnotation", pattern = pattern, isIgnored = true)
            model("$idea/quickfix/removeArgument", pattern = pattern)
            model("$idea/quickfix/removeAtFromAnnotationArgument", pattern = pattern)
            model("$idea/quickfix/removeDefaultParameterValue", pattern = pattern)
            model("$idea/quickfix/removeFinalUpperBound", pattern = pattern)
            model("$idea/quickfix/removeNoConstructor", pattern = pattern)
            model("$idea/quickfix/removeRedundantAssignment", pattern = pattern)
            model("$idea/quickfix/removeRedundantInitializer", pattern = pattern)
            model("$idea/quickfix/removeRedundantLabel", pattern = pattern, isIgnored = true)
            model("$idea/quickfix/removeSingleLambdaParameter", pattern = pattern, isIgnored = true)
            model("$idea/quickfix/removeSuspend", pattern = pattern, isIgnored = true)
            model("$idea/quickfix/removeToStringInStringTemplate", pattern = pattern)
            model("$idea/quickfix/removeTypeVariance", pattern = pattern)
            model("$idea/quickfix/removeUnused", pattern = pattern)
            model("$idea/quickfix/removeUnusedParameter", pattern = pattern, isIgnored = true)
            model("$idea/quickfix/removeUnusedReceiver", pattern = pattern)
            model("$idea/quickfix/removeUseSiteTarget", pattern = pattern)
            model("$idea/quickfix/renameToUnderscore", pattern = pattern, isIgnored = true)
            model("$idea/quickfix/renameUnresolvedReference", pattern = pattern, isIgnored = true)
            model("$idea/quickfix/reorderParameters", pattern = pattern)
            model("$idea/quickfix/replaceJvmFieldWithConst", pattern = pattern)
            model("$idea/quickfix/restrictedRetentionForExpressionAnnotation", pattern = pattern)
            model("$idea/quickfix/simplifyComparison", pattern = pattern)
            model("$idea/quickfix/simplifyExpression", pattern = pattern)
            model("$idea/quickfix/smartCastImpossibleInIfThen", pattern = pattern)
            model("$idea/quickfix/specifyAllRemainingArgumentsByName", pattern = pattern)
            model("$idea/quickfix/specifyOverrideExplicitly", pattern = pattern)
            model("$idea/quickfix/specifyRemainingRequiredArgumentsByName", pattern = pattern)
            model("$idea/quickfix/specifySuperExplicitly", pattern = pattern)
            model("$idea/quickfix/specifyTypeExplicitly", pattern = pattern)
            model("$idea/quickfix/superTypeIsExtensionType", pattern = pattern)
            model("$idea/quickfix/surroundWithNullCheck", pattern = pattern)
            model("$idea/quickfix/suspiciousCollectionReassignment", pattern = pattern)
            model("$idea/quickfix/tooLongCharLiteralToString", pattern = pattern)
            model("$idea/quickfix/typeImports", pattern = pattern)
            model("$idea/quickfix/typeInferenceExpectedTypeMismatch", pattern = pattern)
            model("$idea/quickfix/typeOfAnnotationMember", pattern = pattern)
            model("$idea/quickfix/typeParameters", pattern = pattern)
            model("$idea/quickfix/typeProjection", pattern = pattern)
            model("$idea/quickfix/renameUnresolvedReference", pattern = pattern)
            model("$idea/quickfix/removeRedundantLabel", pattern = pattern)
            model("$idea/quickfix/unnecessaryLateinit", pattern = pattern)
            model("$idea/quickfix/unusedSuppressAnnotation", pattern = pattern, isIgnored = true)
            model("$idea/quickfix/variables", pattern = pattern, isRecursive = false, isIgnored = true)
            model("$idea/quickfix/variables/changeToPropertyAccess", pattern = pattern, isRecursive = false, isIgnored = true)
            model("$idea/quickfix/variables/changeToFunctionInvocation", pattern = pattern, isRecursive = false)
            model("$idea/quickfix/wrapArgumentWithParentheses", pattern = pattern)
            model("$idea/quickfix/wrapWhenExpressionInParentheses", pattern = pattern)
            model("$idea/quickfix/wrongLongSuffix", pattern = pattern)
            model("$idea/quickfix/yieldUnsupported", pattern = pattern, isIgnored = true)
            model("$idea/quickfix/changeSuperTypeListEntryTypeArgument", pattern = pattern, isRecursive = false)
            model("$idea/quickfix/publicApiImplicitType", pattern = pattern)
        }

        testClass<AbstractHighLevelQuickFixMultiFileTest> {
            val pattern = Patterns.forRegex("""^(\w+)\.((before\.Main\.\w+)|(test))$""")
            val testMethodName = "doTestWithExtraFile"
            model(
                "$idea/quickfix/autoImports",
                pattern = pattern,
                testMethodName = testMethodName,
            )
            model(
                "$idea/quickfix/surroundWithNullCheck",
                pattern = pattern,
                testMethodName = testMethodName,
            )
            model(
                "$idea/quickfix/modifiers/addOpenToClassDeclaration",
                pattern = pattern,
                testMethodName = testMethodName,
            )
            model(
                "$idea/quickfix/addGenericUpperBound",
                pattern = pattern,
                testMethodName = testMethodName,
            )
            model(
                "$idea/quickfix/migration/javaAnnotationPositionedArguments",
                pattern = pattern,
                testMethodName = testMethodName,
            )
            model(
                "$idea/quickfix/deprecatedSymbolUsage/imports",
                pattern = pattern,
                testMethodName = testMethodName,
            )
            model(
                "$idea/quickfix/deprecatedSymbolUsage/classUsages/wholeProject",
                pattern = pattern,
                testMethodName = testMethodName,
            )
            model(
                "$idea/quickfix/override/overriddenJavaAccessor",
                pattern = pattern,
                isRecursive = false,
                testMethodName = testMethodName,
            )
            model(
                "$idea/quickfix/createFromUsage/createFunction",
                pattern = pattern,
                testMethodName = testMethodName,
                excludedDirectories = listOf("typeArguments")
            )
            model(
                "$idea/quickfix/memberVisibilityCanBePrivate",
                pattern = pattern,
                testMethodName = testMethodName
            )
        }

        testClass<AbstractHighLevelQuickFixMultiModuleTest> {
            model("$idea/multiModuleQuickFix", pattern = DIRECTORY, depth = 1, excludedDirectories = listOf("addDependency"))
        }

        testClass<AbstractHighLevelWithPostponedQuickFixMultiModuleTest> {
            model("$idea/multiModuleQuickFix/addDependency", pattern = DIRECTORY, isRecursive = false)
        }
    }
}
