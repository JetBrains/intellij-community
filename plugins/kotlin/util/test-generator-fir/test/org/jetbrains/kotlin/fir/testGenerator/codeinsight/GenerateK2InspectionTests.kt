// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.fir.testGenerator.codeinsight

import org.jetbrains.kotlin.idea.k2.codeInsight.inspections.shared.AbstractK2SharedQuickFixTest
import org.jetbrains.kotlin.idea.k2.codeInsight.inspections.shared.AbstractSharedK2InspectionTest
import org.jetbrains.kotlin.idea.k2.codeInsight.inspections.shared.AbstractSharedK2LocalInspectionTest
import org.jetbrains.kotlin.idea.k2.codeInsight.inspections.shared.AbstractSharedK2MultiFileQuickFixTest
import org.jetbrains.kotlin.idea.k2.codeInsight.inspections.shared.idea.kdoc.AbstractSharedK2KDocHighlightingTest
import org.jetbrains.kotlin.idea.k2.inspections.tests.*
import org.jetbrains.kotlin.idea.k2.quickfix.tests.AbstractK2MultiFileQuickFixTest
import org.jetbrains.kotlin.idea.k2.quickfix.tests.AbstractK2QuickFixTest
import org.jetbrains.kotlin.testGenerator.model.*
import org.jetbrains.kotlin.testGenerator.model.GroupCategory.*
import org.jetbrains.kotlin.testGenerator.model.Patterns.DIRECTORY


internal fun MutableTWorkspace.generateK2InspectionTests() {
    val idea = "idea/tests/testData/"

    testGroup("code-insight/inspections-k2/tests", category = INSPECTIONS, testDataPath = "../../..") {
        testClass<AbstractK2LocalInspectionTest>(commonSuite = false) {
            val pattern = Patterns.forRegex("^([\\w\\-_\\.]+)\\.(kt|kts)$")
            model("${idea}/inspectionsLocal/unusedVariable", pattern = pattern)
            model("${idea}/inspectionsLocal/redundantVisibilityModifier", pattern = pattern)
            model("${idea}/inspectionsLocal/redundantWith", pattern = pattern)
            model("${idea}/inspectionsLocal/implicitThis")
            model("${idea}/inspectionsLocal/redundantInnerClassModifier")
            model("${idea}/inspectionsLocal/doubleNegation")
            model("${idea}/inspectionsLocal/safeCastWithReturn")
            model("${idea}/intentions/removeExplicitSuperQualifier")
            model("${idea}/inspectionsLocal/enumValuesSoftDeprecate")
            model("${idea}/inspectionsLocal/branched/ifThenToElvis", pattern = Patterns.KT_WITHOUT_DOTS)
            model("${idea}/inspectionsLocal/branched/ifThenToSafeAccess", pattern = Patterns.KT_WITHOUT_DOTS)
            model("${idea}/inspectionsLocal/conventionNameCalls/replaceGetOrSet")
            model("${idea}/inspectionsLocal/cascadeIf")
            model("${idea}/inspectionsLocal/nullableBooleanElvis")
            model("${idea}/inspectionsLocal/redundantElvisReturnNull")
            model("${idea}/inspectionsLocal/replaceCollectionCountWithSize")
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
            model("${idea}/inspectionsLocal/suspiciousCascadingIf")
            model("${idea}/inspectionsLocal/equalsOrHashCode")
            model("${idea}/inspectionsLocal/removeRedundantQualifierName")
            model("${idea}/inspectionsLocal/redundantUnitExpression")
            model("${idea}/inspectionsLocal/useExpressionBody")
            model("${idea}/inspectionsLocal/equalsBetweenInconvertibleTypes")
            model("${idea}/inspectionsLocal/covariantEquals")
            model("${idea}/inspectionsLocal/explicitThis")
            model("${idea}/inspectionsLocal/redundantIf")
            model("${idea}/inspectionsLocal/redundantLambdaArrow")
            model("${idea}/inspectionsLocal/redundantLambdaOrAnonymousFunction")
            model("${idea}/inspectionsLocal/mayBeConstant")
            model("${idea}/inspectionsLocal/moveLambdaOutsideParentheses")
            model("${idea}/inspectionsLocal/foldInitializerAndIfToElvis")
            model("${idea}/inspectionsLocal/redundantElseInIf")
            model("${idea}/inspectionsLocal/redundantExplicitType")
            model("${idea}/intentions/convertArgumentToSet")
            model("${idea}/inspectionsLocal/coroutines/redundantRunCatching")
            model("${idea}/inspectionsLocal/coroutines/unusedFlow")
            model("${idea}/inspectionsLocal/joinDeclarationAndAssignment")
            model("${idea}/inspectionsLocal/replaceArrayOfWithLiteral")
            model("${idea}/inspectionsLocal/selfAssignment")
            model("${idea}/inspectionsLocal/replaceJavaStaticMethodWithKotlinAnalog")
            // unusedSymbol is covered with K2UnusedSymbolHighlightingTestGenerated
            //model("${idea}/inspectionsLocal/unusedSymbol", pattern = pattern)
            model("${idea}/inspectionsLocal/branched/introduceWhenSubject")
            model("${idea}/inspectionsLocal/usePropertyAccessSyntax")
            model("${idea}/inspectionsLocal/redundantUnitReturnType")
            model("${idea}/inspectionsLocal/suspiciousCollectionReassignment")
            model("${idea}/inspectionsLocal/suspiciousVarProperty")
            model("${idea}/inspectionsLocal/unnecessaryVariable")
            model("${idea}/inspectionsLocal/canBeParameter")
            model("${idea}/inspectionsLocal/arrayInDataClass")
            model("${idea}/inspectionsLocal/collections/simplifiableCallChain")
            model("${idea}/inspectionsLocal/collections/redundantAsSequence")
            model("${idea}/inspectionsLocal/canSimplifyDollarLiteral")
            model("${idea}/inspectionsLocal/canConvertToMultiDollarString")
            model("${idea}/inspectionsLocal/floatingPointLiteralPrecision")
            model("code-insight/inspections-k2/tests/testData/inspectionsLocal", pattern = pattern)
            model("${idea}/inspectionsLocal/replaceIsEmptyWithIfEmpty")
            model("${idea}/inspectionsLocal/booleanLiteralArgument")
            model("${idea}/inspectionsLocal/replaceArrayEqualityOpWithArraysEquals")
            model("${idea}/inspectionsLocal/nestedLambdaShadowedImplicitParameter")
            model("${idea}/inspectionsLocal/unusedReceiverParameter")
            model("${idea}/inspectionsLocal/filterIsInstanceAlwaysEmpty")
            model("${idea}/inspectionsLocal/selfReferenceConstructorParameter")
            model("${idea}/inspectionsLocal/simplifyAssertNotNull")
            model("${idea}/inspectionsLocal/canBeVal")
            model("${idea}/inspectionsLocal/mapGetWithNotNullAssertionOperator")
            model("${idea}/inspectionsLocal/replaceSubstring")
            model("${idea}/inspectionsLocal/replaceWithIgnoreCaseEquals")
            model("${idea}/inspectionsLocal/replaceToWithInfixForm")
            model("${idea}/inspectionsLocal/addOperatorModifier")
            model("${idea}/inspectionsLocal/kotlinUnreachableCode")
            model("${idea}/inspectionsLocal/removeRedundantLabel")
            model("${idea}/inspectionsLocal/removeRedundantCallsOfConversionMethods")
            model("${idea}/inspectionsLocal/removeExplicitTypeArguments")
            model("${idea}/inspectionsLocal/contextParametersMigration")
            model("${idea}/inspectionsLocal/defaultAnnotationTarget")
            model("${idea}/inspectionsLocal/redundantEnumConstructorInvocation")
            model("${idea}/inspectionsLocal/orInWhenGuard")
            model("${idea}/inspectionsLocal/convertFromMultiDollarToRegularString")
            model("${idea}/inspectionsLocal/redundantCompanionReference")
            model("${idea}/inspectionsLocal/replacePutWithAssignment")
            model("${idea}/inspectionsLocal/replaceRangeStartEndInclusiveWithFirstLast")
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
            model("${idea}/inspectionsLocal/javaCollectionsWithNullableTypes")
            model("${idea}/inspectionsLocal/redundantNullableReturnType")
            model("${idea}/inspectionsLocal/copyWithoutNamedArguments")
            model("${idea}/inspectionsLocal/unusedUnaryOperator")
            model("${idea}/inspectionsLocal/javaMapForEach")
            model("${idea}/inspectionsLocal/functionWithLambdaExpressionBody")

            // There is no `RemoveExplicitTypeArgumentsIntention` in K2 because `RemoveExplicitTypeArgumentsInspection` is available
            // and the inspection can have the "No highlighting (fix available)" severity.
            // Therefore, we generate a test for the inspection based on the tests for K1-RemoveExplicitTypeArgumentsIntention.
            model("${idea}/intentions/removeExplicitTypeArguments", testClassName = "RemoveExplicitTypeArgumentsFormerIntentionTest")
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
            model("${idea}/inspections/replaceArrayEqualityOpWithArraysEquals", pattern = pattern)
            model("${idea}/inspections/memberVisibilityCanBePrivate", pattern = pattern)
            model("${idea}/inspections/canConvertToMultiDollarString", pattern = pattern)
            model("${idea}/intentions/removeExplicitTypeArguments", pattern = pattern)
        }

        testClass<AbstractK2MultiFileInspectionTest> {
            model("${idea}/multiFileInspections/mismatchedPackageDirectoryWithEmptyKts", pattern = Patterns.TEST)
            model("${idea}/multiFileInspections/mismatchedProjectAndDirectory", pattern = Patterns.TEST)
            model("${idea}/multiFileInspections/mismatchedProjectAndDirectoryRoot", pattern = Patterns.TEST)
        }

        testClass<AbstractK2MultiFileLocalInspectionTest> {
            val pattern = Patterns.forRegex("^([\\w\\-_]+)\\.test$")
            model("${idea}/multiFileLocalInspections/unusedSymbol", pattern = pattern)
            model("${idea}/multiFileLocalInspections/reconcilePackageWithDirectory", pattern = pattern)
            model("${idea}/multiFileLocalInspections/redundantQualifierName", pattern = pattern)
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
            model("${idea}/quickfix/optIn", pattern = pattern)
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

    testGroup("code-insight/inspections-shared/tests/k2", category = INSPECTIONS, testDataPath = "../testData") {
        testClass<AbstractSharedK2LocalInspectionTest> {
            val pattern = Patterns.forRegex("^([\\w\\-_]+)\\.(kt|kts)$")
            model("inspectionsLocal", pattern = pattern)
        }

        testClass<AbstractSharedK2InspectionTest> {
            val pattern = Patterns.forRegex("^(inspections\\.test)$")
            model("inspections", pattern = pattern)
            model("inspectionsLocal", pattern = pattern)
        }
    }

    testGroup("code-insight/inspections-shared/tests/k2", category = HIGHLIGHTING, testDataPath = "../testData") {
        testClass<AbstractSharedK2KDocHighlightingTest> {
            val pattern = Patterns.forRegex("^([\\w\\-_]+)\\.(kt|kts)$")
            model("kdoc/highlighting", pattern = pattern)
        }
    }

    testGroup("code-insight/inspections-shared/tests/k2", category = QUICKFIXES, testDataPath = "../testData") {
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
    }
}
