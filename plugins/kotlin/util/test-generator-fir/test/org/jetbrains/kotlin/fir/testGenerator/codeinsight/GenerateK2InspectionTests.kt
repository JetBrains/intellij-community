// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.fir.testGenerator.codeinsight

import org.jetbrains.kotlin.idea.inspections.AbstractCoroutineNonBlockingContextDetectionTest
import org.jetbrains.kotlin.idea.inspections.AbstractK2SharedQuickFixTest
import org.jetbrains.kotlin.idea.inspections.AbstractSharedK2InspectionTest
import org.jetbrains.kotlin.idea.inspections.AbstractSharedK2MultiFileQuickFixTest
import org.jetbrains.kotlin.idea.inspections.tests.AbstractAllOpenLocalInspectionTest
import org.jetbrains.kotlin.idea.inspections.tests.AbstractK2ActualExpectTest
import org.jetbrains.kotlin.idea.inspections.tests.AbstractK2AmbiguousActualsTest
import org.jetbrains.kotlin.idea.inspections.tests.AbstractK2InspectionTest
import org.jetbrains.kotlin.idea.inspections.tests.AbstractK2LocalInspectionAndGeneralHighlightingTest
import org.jetbrains.kotlin.idea.inspections.tests.AbstractK2LocalInspectionTest
import org.jetbrains.kotlin.idea.inspections.tests.AbstractK2MultiFileInspectionTest
import org.jetbrains.kotlin.idea.inspections.tests.AbstractK2MultiFileLocalInspectionTest
import org.jetbrains.kotlin.idea.quickfix.tests.AbstractK2MultiFileQuickFixTest
import org.jetbrains.kotlin.idea.quickfix.tests.AbstractK2QuickFixTest
import org.jetbrains.kotlin.testGenerator.model.GroupCategory.INSPECTIONS
import org.jetbrains.kotlin.testGenerator.model.GroupCategory.QUICKFIXES
import org.jetbrains.kotlin.testGenerator.model.MutableTWorkspace
import org.jetbrains.kotlin.testGenerator.model.Patterns
import org.jetbrains.kotlin.testGenerator.model.Patterns.DIRECTORY
import org.jetbrains.kotlin.testGenerator.model.Patterns.KT
import org.jetbrains.kotlin.testGenerator.model.model
import org.jetbrains.kotlin.testGenerator.model.testClass
import org.jetbrains.kotlin.testGenerator.model.testGroup


internal fun MutableTWorkspace.generateK2InspectionTests() {
    val idea = "idea/tests/testData/"

    testGroup("code-insight/inspections-k2/tests", category = INSPECTIONS, testDataPath = "../../..") {
        testClass<AbstractK2LocalInspectionTest>(commonSuite = false) {
            val pattern = Patterns.forRegex("^([\\w\\-_\\.]+)\\.(kt|kts)$")
            model("${idea}/inspectionsLocal/unusedVariable", pattern = pattern)
            model("${idea}/inspectionsLocal/redundantVisibilityModifier", pattern = pattern)
            model("${idea}/inspectionsLocal/unusedEquals")
            model("${idea}/inspectionsLocal/recursiveEqualsCall")
            model("${idea}/inspectionsLocal/redundantWith", pattern = pattern)
            model("${idea}/inspectionsLocal/implicitThis")
            model("${idea}/inspectionsLocal/redundantInnerClassModifier")
            model("${idea}/inspectionsLocal/doubleBang")
            model("${idea}/inspectionsLocal/doubleNegation")
            model("${idea}/inspectionsLocal/emptyRange")
            model("${idea}/inspectionsLocal/safeCastWithReturn")
            model("${idea}/intentions/removeExplicitSuperQualifier")
            model("${idea}/intentions/destructuringInLambda")
            model("${idea}/intentions/destructuringVariables")
            model("${idea}/intentions/iterationOverMap")
            model("${idea}/intentions/removeSingleExpressionStringTemplate")
            model("${idea}/inspectionsLocal/enumValuesSoftDeprecate")
            model("${idea}/inspectionsLocal/branched/ifThenToElvis", pattern = Patterns.KT_WITHOUT_DOTS)
            model("${idea}/inspectionsLocal/branched/ifThenToSafeAccess", pattern = Patterns.KT_WITHOUT_DOTS)
            model("${idea}/inspectionsLocal/conventionNameCalls/replaceGetOrSet")
            model("${idea}/inspectionsLocal/cascadeIf")
            model("${idea}/inspectionsLocal/nullChecksToSafeCall")
            model("${idea}/inspectionsLocal/nullableBooleanElvis")
            model("${idea}/inspectionsLocal/nullableHashCode")
            model("${idea}/inspectionsLocal/redundantElvisReturnNull")
            model("${idea}/inspectionsLocal/replaceCollectionCountWithSize")
            model("${idea}/inspectionsLocal/nonNullableBooleanPropertyInExternalInterface", pattern = Patterns.KT_WITHOUT_DOTS)
            model("${idea}/inspectionsLocal/nonExternalClassifierExtendingStateOrProps", pattern = Patterns.KT_WITHOUT_DOTS)
            model("${idea}/inspectionsLocal/nonVarPropertyInExternalInterface", pattern = Patterns.KT_WITHOUT_DOTS)
            model("${idea}/inspectionsLocal/removeToStringInStringTemplate")
            model("${idea}/inspectionsLocal/liftOut/ifToAssignment")
            model("${idea}/inspectionsLocal/liftOut/tryToAssignment")
            model("${idea}/inspectionsLocal/liftOut/whenToAssignment")
            model("${idea}/inspectionsLocal/liftOut/ifToReturn")
            model("${idea}/inspectionsLocal/liftOut/tryToReturn")
            model("${idea}/inspectionsLocal/liftOut/whenToReturn")
            model("${idea}/inspectionsLocal/inconsistentCommentForJavaParameter")
            model("${idea}/inspectionsLocal/scopeFunctions")
            model("${idea}/inspectionsLocal/whenWithOnlyElse")
            model("${idea}/inspectionsLocal/redundantRequireNotNullCall")
            model("${idea}/inspectionsLocal/suspiciousCallOnCollectionToAddOrRemovePath")
            model("${idea}/inspectionsLocal/arrayHashCode")
            model("${idea}/inspectionsLocal/misorderedAssertEqualsArguments", pattern = Patterns.KT_WITHOUT_DOTS)
            model("${idea}/inspectionsLocal/arrayToString")
            model("${idea}/inspectionsLocal/stringReferentialEquality")
            model("${idea}/inspectionsLocal/suspiciousCascadingIf")
            model("${idea}/inspectionsLocal/equalsOrHashCode")
            model("${idea}/inspectionsLocal/removeRedundantQualifierName")
            model("${idea}/inspectionsLocal/redundantUnitExpression")
            model("${idea}/inspectionsLocal/useExpressionBody")
            model("${idea}/inspectionsLocal/suspiciousCallableReferenceInLambda")
            model("${idea}/inspectionsLocal/equalsBetweenInconvertibleTypes")
            model("${idea}/inspectionsLocal/covariantEquals")
            model("${idea}/inspectionsLocal/explicitThis")
            model("${idea}/inspectionsLocal/redundantIf")
            model("${idea}/intentions/convertTryFinallyToUseCall")
            model("${idea}/inspectionsLocal/redundantLambdaArrow")
            model("${idea}/inspectionsLocal/redundantLambdaOrAnonymousFunction")
            model("${idea}/inspectionsLocal/mayBeConstant")
            model("${idea}/inspectionsLocal/moveLambdaOutsideParentheses")
            model("${idea}/inspectionsLocal/foldInitializerAndIfToElvis")
            model("${idea}/inspectionsLocal/redundantElseInIf")
            model("${idea}/inspectionsLocal/kdocMissingDocumentation")
            model("${idea}/inspectionsLocal/redundantExplicitType")
            model("${idea}/intentions/convertArgumentToSet")
            model("${idea}/intentions/replaceSizeCheckWithIsNotEmpty")
            model("${idea}/inspectionsLocal/coroutines/redundantRunCatching")
            model("${idea}/inspectionsLocal/coroutines/simplifiableFlowCallChain")
            model("${idea}/inspectionsLocal/coroutines/simplifiableFlowCall")
            model("${idea}/inspectionsLocal/coroutines/unusedFlow")
            model("${idea}/inspectionsLocal/coroutines/uselessCallOnFlow")
            model("${idea}/inspectionsLocal/joinDeclarationAndAssignment")
            model("${idea}/inspectionsLocal/replaceArrayOfWithLiteral")
            model("${idea}/inspectionsLocal/selfAssignment")
            model("${idea}/inspectionsLocal/replaceJavaStaticMethodWithKotlinAnalog")
            model("${idea}/inspectionsLocal/replaceManualRangeWithIndicesCalls")
            // unusedSymbol is covered with K2UnusedSymbolHighlightingTestGenerated
            //model("${idea}/inspectionsLocal/unusedSymbol", pattern = pattern)
            model("${idea}/inspectionsLocal/branched/introduceWhenSubject")
            model("${idea}/inspectionsLocal/usePropertyAccessSyntax", pattern = Patterns.KT_WITHOUT_DOTS)
            model("${idea}/inspectionsLocal/unlabeledReturnInsideLambda")
            model("${idea}/inspectionsLocal/redundantUnitReturnType")
            model("${idea}/inspectionsLocal/suspiciousCollectionReassignment")
            model("${idea}/inspectionsLocal/unnecessaryVariable")
            model("${idea}/inspectionsLocal/canBeParameter")
            model("${idea}/inspectionsLocal/arrayInDataClass")
            model("${idea}/inspectionsLocal/collections/simplifiableCallChain")
            model("${idea}/inspectionsLocal/collections/redundantAsSequence")
            model("${idea}/inspectionsLocal/collections/simplifiableCall")
            model("${idea}/inspectionsLocal/canSimplifyDollarLiteral")
            model("${idea}/inspectionsLocal/canConvertToMultiDollarString")
            model("${idea}/inspectionsLocal/floatingPointLiteralPrecision")
            model("code-insight/inspections-k2/tests/testData/inspectionsLocal", pattern = pattern)
            model("${idea}/inspectionsLocal/replaceIsEmptyWithIfEmpty")
            model("${idea}/inspectionsLocal/booleanLiteralArgument")
            model("${idea}/inspectionsLocal/verboseNullabilityAndEmptiness")
            model("${idea}/inspectionsLocal/nestedLambdaShadowedImplicitParameter")
            model("${idea}/inspectionsLocal/unusedReceiverParameter")
            model("${idea}/inspectionsLocal/filterIsInstanceAlwaysEmpty")
            model("${idea}/inspectionsLocal/selfReferenceConstructorParameter")
            model("${idea}/inspectionsLocal/simplifyAssertNotNull")
            model("${idea}/inspectionsLocal/simplifyNestedEachInScope")
            model("${idea}/inspectionsLocal/canBeVal")
            model("${idea}/inspectionsLocal/mapGetWithNotNullAssertionOperator")
            model("${idea}/inspectionsLocal/replaceSubstring")
            model("${idea}/inspectionsLocal/replaceWithIgnoreCaseEquals")
            model("${idea}/inspectionsLocal/replaceWithImportAlias")
            model("${idea}/inspectionsLocal/replaceToWithInfixForm")
            model("${idea}/inspectionsLocal/addOperatorModifier")
            model("${idea}/inspectionsLocal/kotlinUnreachableCode")
            model("${idea}/inspectionsLocal/removeRedundantLabel")
            model("${idea}/inspectionsLocal/logging/loggerInitializedWithForeignClass")
            model("${idea}/inspectionsLocal/mainFunctionReturnUnit")
            model("${idea}/inspectionsLocal/replaceIfExpressionWithFirstOrNull")

            // removeRedundantCallsOfConversionMethods is implemented as compiler diagnostic, see quickfixes
            model("${idea}/inspectionsLocal/removeRedundantCallsOfConversionMethods", isIgnored = true)

            model("${idea}/inspectionsLocal/removeExplicitTypeArguments")
            model("${idea}/inspectionsLocal/defaultAnnotationTarget")
            model("${idea}/inspectionsLocal/redundantEnumConstructorInvocation")
            model("${idea}/inspectionsLocal/redundantReturnKeyword")
            model("${idea}/inspectionsLocal/orInWhenGuard")
            model("${idea}/inspectionsLocal/customComponentDestructuringMigration")
            model("${idea}/inspectionsLocal/convertFromMultiDollarToRegularString")
            model("${idea}/inspectionsLocal/convertExplicitContextArgumentToImplicit")
            model("${idea}/inspectionsLocal/convertImplicitContextArgumentToExplicit")
            model("${idea}/inspectionsLocal/redundantCompanionReference")
            model("${idea}/inspectionsLocal/replacePutWithAssignment")
            model("${idea}/inspectionsLocal/replaceRangeStartEndInclusiveWithFirstLast")
            model("${idea}/inspectionsLocal/replaceStringFormatWithLiteral")
            model("${idea}/inspectionsLocal/replaceNegatedIsEmptyWithIsNotEmpty")
            model("${idea}/inspectionsLocal/removeRedundantSpreadOperator")
            model("${idea}/inspectionsLocal/convertPairConstructorToToFunction")
            model("${idea}/inspectionsLocal/removeEmptyParenthesesFromAnnotationEntry")
            model("${idea}/inspectionsLocal/incompleteDestructuringInspection")
            model("${idea}/inspectionsLocal/redundantObjectTypeCheck")
            model("${idea}/inspectionsLocal/replaceWithOperatorAssignment")
            model("${idea}/inspectionsLocal/unusedLambdaExpressionBody")
            model("${idea}/inspectionsLocal/lateinitVarOverridesLateinitVar")
            model("${idea}/inspectionsLocal/replaceNotNullAssertionWithElvisReturn")
            model("${idea}/inspectionsLocal/replaceGuardClauseWithFunctionCall")
            model("${idea}/inspectionsLocal/convertNaNEquality")
            model("${idea}/inspectionsLocal/convertLongToDuration")
            model("${idea}/inspectionsLocal/replaceWithEnumMap")
            model("${idea}/inspectionsLocal/javaCollectionsWithNullableTypes")
            model("${idea}/inspectionsLocal/deprecatedCallableAddReplaceWith", pattern = Patterns.KT_WITHOUT_DOTS)
            model("${idea}/inspectionsLocal/redundantNullableReturnType")
            model("${idea}/inspectionsLocal/copyWithoutNamedArguments")
            model("${idea}/inspectionsLocal/unusedUnaryOperator")
            model("${idea}/inspectionsLocal/javaMapForEach")
            model("${idea}/inspectionsLocal/mapToForEach")
            model("${idea}/inspectionsLocal/functionWithLambdaExpressionBody")
            model("${idea}/inspectionsLocal/convertSealedSubClassToObject", pattern = Patterns.KT_WITHOUT_DOTS)
            model("${idea}/inspectionsLocal/replaceUntilWithRangeUntil")
            model("${idea}/inspectionsLocal/scriptExecutable", pattern = Patterns.KTS)
            model("${idea}/inspectionsLocal/replaceAddAllWithMapTo")
            model("${idea}/inspectionsLocal/unnecessaryOptInAnnotation")
            model("${idea}/inspectionsLocal/duplicateArgumentsInSetOfAndMapOfFunctions")

            // There is no `RemoveExplicitTypeArgumentsIntention` in K2 because `RemoveExplicitTypeArgumentsInspection` is available
            // and the inspection can have the "No highlighting (fix available)" severity.
            // Therefore, we generate a test for the inspection based on the tests for K1-RemoveExplicitTypeArgumentsIntention.
            model("${idea}/intentions/removeExplicitTypeArguments", testClassName = "RemoveExplicitTypeArgumentsFormerIntentionTest", pattern = pattern)
            model("${idea}/intentions/convertReferenceToLambda", pattern = pattern)
        }

        testClass<AbstractAllOpenLocalInspectionTest> {
            model("${idea}/inspectionsPlugins/allOpen/local", pattern = Patterns.KT_WITHOUT_DOTS)
        }

        /**
         * `unusedSymbol` tests require [com.intellij.codeInsight.daemon.impl.GeneralHighlightingPass] to run,
         * so they extend the other base class [AbstractK2LocalInspectionAndGeneralHighlightingTest]
         */
        val packageName = AbstractK2LocalInspectionAndGeneralHighlightingTest::class.java.`package`.name
        val generatedClassName = "$packageName.K2UnusedSymbolHighlightingTestGenerated"
        testClass<AbstractK2LocalInspectionAndGeneralHighlightingTest>(generatedClassName) {
            model("${idea}/inspectionsLocal/unusedSymbol", pattern = Patterns.KT_WITHOUT_DOTS)
        }

        testClass<AbstractK2InspectionTest> {
            val pattern = Patterns.forRegex("^(inspections\\.test)$")
            model("${idea}/inspections/enumValuesSoftDeprecateInJava", pattern = pattern)
            model("${idea}/inspections/enumValuesSoftDeprecateInKotlin", pattern = pattern)
            model("${idea}/inspections/redundantIf", pattern = pattern)
            model("${idea}/inspections/equalsAndHashCode", pattern = pattern)
            model("${idea}/inspections/protectedInFinal", pattern = pattern)
            model("${idea}/intentions/convertToStringTemplate", pattern = pattern)
            model("${idea}/inspections/unusedSymbol", pattern = pattern)
            model("${idea}/inspections/arrayInDataClass", pattern = pattern)
            model("${idea}/inspections/unusedLambdaExpressionBody", pattern = pattern)
            model("${idea}/inspections/publicApiImplicitType", pattern = pattern)
            model("${idea}/inspections/memberVisibilityCanBePrivate", pattern = pattern)
            model("${idea}/inspections/canConvertToMultiDollarString", pattern = pattern)
            model("${idea}/intentions/removeExplicitTypeArguments", pattern = pattern)
        }

        testClass<AbstractK2MultiFileInspectionTest> {
            model("${idea}/multiFileInspections", pattern = Patterns.TEST)
        }

        testClass<AbstractK2MultiFileLocalInspectionTest> {
            val pattern = Patterns.forRegex("^([\\w\\-_]+)\\.test$")
            model("${idea}/multiFileLocalInspections/unusedSymbol", pattern = pattern)
            model("${idea}/multiFileLocalInspections/reconcilePackageWithDirectory", pattern = pattern)
            model("${idea}/multiFileLocalInspections/convertSealedSubClassToObject", pattern = pattern)
            model("${idea}/multiFileLocalInspections/redundantQualifierName", pattern = pattern)
            model("${idea}/multiFileLocalInspections/moveFileToPackageMatchingDirectory", pattern = pattern)
            model("${idea}/multiFileLocalInspections/usePropertyAccessSyntax", pattern = pattern, flatten = true)
            model("code-insight/inspections-k2/tests/testData/multiFileInspectionsLocal", pattern = pattern)
        }

        testClass<AbstractK2AmbiguousActualsTest> {
            model("${idea}/multiplatform/ambiguousActuals", pattern = DIRECTORY, isRecursive = false)
        }

        testClass<AbstractK2ActualExpectTest> {
            val pattern = Patterns.forRegex("^([\\w\\-_]+)\\.kt$")
            model("code-insight/inspections-k2/tests/testData/multiplatform/actualExpect/", pattern = pattern, isRecursive = false)
        }
    }
    testGroup("code-insight/inspections-k2/tests", category = QUICKFIXES, testDataPath = "../../..") {
        testClass<AbstractK2QuickFixTest> {
            val pattern = Patterns.forRegex("^([\\w\\-_]+)\\.kt$")
            model("${idea}/quickfix/redundantIf", pattern = pattern)
            model("${idea}/quickfix/changeSignature", pattern = pattern)
            model("${idea}/quickfix/redundantModalityModifier", pattern = pattern)
            model("${idea}/quickfix/removeToStringInStringTemplate", pattern = pattern)
            model("${idea}/quickfix/suppress", pattern = pattern)
            model("${idea}/quickfix/suspiciousCollectionReassignment", pattern = pattern)
            model("${idea}/quickfix/removeAnnotation", pattern = pattern)
            model("${idea}/quickfix/optIn", pattern = Patterns.KT_OR_KTS)
            model("${idea}/quickfix/removeUseSiteTarget", pattern = pattern)
            model("${idea}/quickfix/protectedInFinal", pattern = pattern)
            model("${idea}/quickfix/redundantInterpolationPrefix", pattern = pattern)
            model("${idea}/quickfix/addInterpolationPrefixUnresolvedReference", pattern = pattern)
            model("${idea}/quickfix/unsupportedFeature", pattern = pattern)
            model("${idea}/intentions/convertSecondaryConstructorToPrimary", pattern = pattern)
        }

        testClass<AbstractK2MultiFileQuickFixTest> {
            val pattern = Patterns.forRegex("""^(\w+)\.((before\.Main\.\w+)|(test))$""")
            model("${idea}/quickfix/optIn", pattern = pattern, testMethodName = "doTestWithExtraFile")
            model("${idea}/quickfix/changeSignature", pattern = pattern, testMethodName = "doTestWithExtraFile")
        }
    }

    testGroup("code-insight/inspections-k2/tests", category = INSPECTIONS) {
        testClass<AbstractSharedK2InspectionTest> {
            val pattern = Patterns.forRegex("^(inspections\\.test)$")
            model("inspections", pattern = pattern)
            model("inspectionsLocal", pattern = pattern)
        }
    }

    testGroup("code-insight/inspections-k2/tests", category = QUICKFIXES) {
        val relativeIdea = "../../../../$idea"
        testClass<AbstractK2SharedQuickFixTest> {
            val pattern = Patterns.forRegex("^([\\w\\-_]+)\\.kt$")
            model("quickfix", pattern = pattern)

            model("${relativeIdea}/quickfix/optimizeImports", pattern = pattern)
        }

        testClass<AbstractSharedK2MultiFileQuickFixTest> {
            val pattern = Patterns.forRegex("""^(\w+)\.((before\.Main\.\w+)|(test))$""")
            model("${relativeIdea}/quickfix/optimizeImports", pattern = pattern, testMethodName = "doTestWithExtraFile")
        }

        testClass<AbstractCoroutineNonBlockingContextDetectionTest>(
            generatedClassName = "org.jetbrains.kotlin.idea.codeInsight.inspections.SharedK2CoroutineNonBlockingContextDetectionTestGenerated"
        ) {
            model("inspections/blockingCallsDetection", pattern = KT)
        }
    }
}
