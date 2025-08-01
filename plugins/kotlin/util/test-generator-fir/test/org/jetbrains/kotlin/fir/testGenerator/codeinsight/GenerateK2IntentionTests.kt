// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.fir.testGenerator.codeinsight

import org.jetbrains.kotlin.idea.k2.codeInsight.intentions.shared.AbstractSharedK2IntentionTest
import org.jetbrains.kotlin.idea.k2.intentions.tests.AbstractK2GotoTestOrCodeActionTest
import org.jetbrains.kotlin.idea.k2.intentions.tests.AbstractK2IntentionInInjectionTest
import org.jetbrains.kotlin.idea.k2.intentions.tests.AbstractK2IntentionTest
import org.jetbrains.kotlin.idea.k2.intentions.tests.AbstractK2MultiFileIntentionTest
import org.jetbrains.kotlin.testGenerator.model.*
import org.jetbrains.kotlin.testGenerator.model.GroupCategory.INTENTIONS
import org.jetbrains.kotlin.testGenerator.model.Patterns.TEST


internal fun MutableTWorkspace.generateK2IntentionTests() {
    val idea = "idea/tests/testData/"

    testGroup("code-insight/intentions-k2/tests", category = INTENTIONS, testDataPath = "../../..") {
        val pattern = Patterns.forRegex("^([\\w\\-_]+)\\.(kt|kts)$")
        testClass<AbstractK2IntentionTest> {
            model("${idea}intentions/addFullQualifier", pattern = pattern)
            model("${idea}intentions/addMissingClassKeyword", pattern = pattern)
            model("${idea}intentions/addNameToArgument", pattern = pattern)
            model("${idea}intentions/addNamesToCallArguments", pattern = pattern)
            model("${idea}intentions/addNamesToFollowingArguments", pattern = pattern)
            model("${idea}intentions/addOpenModifier", pattern = pattern)
            model("${idea}intentions/addPropertyAccessors", pattern = pattern)
            model("${idea}intentions/specifyTypeExplicitly", pattern = pattern)
            model("${idea}intentions/importAllMembers", pattern = pattern)
            model("${idea}intentions/importMember", pattern = pattern)
            model("${idea}intentions/chop", pattern = pattern)
            model("${idea}intentions/convertConcatenationToBuildString", pattern = pattern)
            model("${idea}intentions/convertLambdaToReference", pattern = pattern)
            model("${idea}intentions/convertStringTemplateToBuildString", pattern = pattern)
            model("${idea}intentions/convertStringTemplateToBuildStringMultiDollarPrefix", pattern = pattern)
            model("${idea}intentions/convertFilteringFunctionWithDemorgansLaw", pattern = pattern)
            model("${idea}intentions/convertToBlockBody", pattern = pattern)
            model("${idea}intentions/removeForLoopIndices", pattern = pattern)
            model("${idea}intentions/addWhenRemainingBranches", pattern = pattern)
            model("${idea}intentions/convertToConcatenatedString", pattern = pattern)
            model("${idea}intentions/convertToStringTemplate", pattern = pattern)
            model("${idea}intentions/convertToStringTemplateInterpolationPrefix", pattern = pattern)
            model("${idea}intentions/convertReferenceToLambda", pattern = pattern)
            model("${idea}intentions/declarations/split", pattern = pattern)
            model("${idea}intentions/removeExplicitType", pattern = pattern)
            model("${idea}intentions/replaceUnderscoreWithTypeArgument", pattern = pattern)
            model("${idea}intentions/convertForEachToForLoop", pattern = pattern)
            model("${idea}intentions/joinArgumentList", pattern = pattern)
            model("${idea}intentions/joinParameterList", pattern = pattern)
            model("${idea}intentions/addNamesInCommentToJavaCallArguments", pattern = pattern)
            model("${idea}intentions/trailingComma", pattern = pattern)
            model("${idea}intentions/insertExplicitTypeArguments", pattern = pattern)
            model("${idea}intentions/removeSingleArgumentName", pattern = pattern)
            model("${idea}intentions/convertUnsafeCastCallToUnsafeCast", pattern = pattern)
            model("${idea}intentions/expandBooleanExpression", pattern = pattern)
            model("${idea}intentions/removeAllArgumentNames", pattern = pattern)
            model("${idea}intentions/convertPropertyGetterToInitializer", pattern = pattern)
            model("${idea}intentions/convertToRawStringTemplate", pattern = pattern)
            model("${idea}intentions/toRawStringLiteral", pattern = pattern)
            model("${idea}intentions/movePropertyToConstructor", pattern = pattern)
            model("${idea}intentions/branched/ifWhen/whenToIf", pattern = pattern)
            model("${idea}intentions/branched/folding/ifToReturnAsymmetrically", pattern = pattern)
            model(
                "code-insight/intentions-k2/tests/testData/intentions",
                pattern = pattern,
                excludedDirectories = listOf("injected")
            )
            model("${idea}intentions/convertBinaryExpressionWithDemorgansLaw", pattern = pattern)
            model("${idea}intentions/invertIfCondition", pattern = pattern)
            model("${idea}intentions/lambdaToAnonymousFunction", pattern = pattern)
            model("${idea}intentions/replaceWithOrdinaryAssignment", pattern = pattern)
            model("${idea}intentions/specifyExplicitLambdaSignature", pattern = pattern)
            model("${idea}intentions/changeVisibility", pattern = pattern)

            //
            model("${idea}intentions/changeVisibility", pattern = pattern, isIgnored = true)
            model("${idea}intentions/evaluateCompileTimeExpression", pattern = pattern)
            model("${idea}intentions/simplifyBooleanWithConstants", pattern = pattern, isIgnored = true)
            model("${idea}intentions/replaceUnderscoreWithParameterName", pattern = pattern, isIgnored = true)
            model("${idea}intentions/convertFunctionToProperty", pattern = pattern)
            model("${idea}intentions/convertTryFinallyToUseCall", pattern = pattern, isIgnored = true)
            model(
                "${idea}intentions/conventionNameCalls",
                pattern = pattern,
                excludedDirectories = listOf("replaceContains") // is implemented as inspection
            )
            model("${idea}intentions/mergeIfs", pattern = pattern, isIgnored = false)
            model("${idea}intentions/convertTrimIndentToTrimMargin", pattern = pattern)
            model("${idea}intentions/iterateExpression", pattern = pattern)
            model("${idea}intentions/objectLiteralToLambda", pattern = pattern, isIgnored = true)
            model("${idea}intentions/infixCallToOrdinary", pattern = pattern, isIgnored = true)
            model("${idea}intentions/convertRangeCheckToTwoComparisons", pattern = pattern, isIgnored = true)
            model("${idea}intentions/removeRedundantCallsOfConversionMethods", pattern = pattern, isIgnored = true)
            model("${idea}intentions/addJvmStatic", pattern = pattern, isIgnored = true)
            model("${idea}intentions/implementAsConstructorParameter", pattern = pattern)
            model("${idea}intentions/insertCurlyBracesToTemplate", pattern = pattern)
            model("${idea}intentions/replaceUntilWithRangeTo", pattern = pattern, isIgnored = true)
            model("${idea}intentions/convertLateinitPropertyToNullable", pattern = pattern)
            model("${idea}intentions/swapStringEqualsIgnoreCase", pattern = pattern, isIgnored = true)
            model("${idea}intentions/replaceExplicitFunctionLiteralParamWithIt", pattern = pattern)
            model("${idea}intentions/nullableBooleanEqualityCheckToElvis", pattern = pattern)
            model("${idea}intentions/replaceWithOrdinaryAssignment", pattern = pattern, isIgnored = true)
            model("${idea}intentions/introduceImportAlias", pattern = pattern)
            model("${idea}intentions/addForLoopIndices", pattern = pattern)
            model("${idea}intentions/moveDeclarationToSeparateFile", pattern = pattern, isIgnored = true)
            model("${idea}intentions/convertCamelCaseTestFunctionToSpaced", pattern = pattern)
            model("${idea}intentions/removeForLoopIndices", pattern = pattern, isIgnored = true)
            model("${idea}intentions/valToObject", pattern = pattern, isIgnored = true)
            model("${idea}intentions/convertPropertyInitializerToGetter", pattern = pattern)
            model("${idea}intentions/convertLambdaToSingleLine", pattern = pattern, isIgnored = true)
            model("${idea}intentions/toInfixCall", pattern = pattern)
            model("${idea}intentions/convertArrayParameterToVararg", pattern = pattern)
            model("${idea}intentions/branched", pattern = pattern, isIgnored = true)
            model("${idea}intentions/convertVariableAssignmentToExpression", pattern = pattern)
            model("${idea}intentions/convertNullablePropertyToLateinit", pattern = pattern, isIgnored = true)
            model("${idea}intentions/replaceSizeCheckWithIsNotEmpty", pattern = pattern, isIgnored = true)
            model("${idea}intentions/replaceTypeArgumentWithUnderscore", pattern = pattern, isIgnored = true)
            model("${idea}intentions/convertSecondaryConstructorToPrimary", pattern = pattern, isIgnored = true)
            model("${idea}intentions/expandBooleanExpression", pattern = pattern, isIgnored = true)
            model("${idea}intentions/destructuringVariables", pattern = pattern, isIgnored = true)
            model("${idea}intentions/convertObjectLiteralToClass", pattern = pattern)
            model("${idea}intentions/toOrdinaryStringLiteral", pattern = pattern, isIgnored = true)
            model("${idea}intentions/convertLineCommentToBlockComment", pattern = pattern, isIgnored = true)
            model("${idea}intentions/declarations/convertMemberToExtension", pattern = pattern)
            model("${idea}intentions/removeEmptyPrimaryConstructor", pattern = pattern, isIgnored = true)
            model("${idea}intentions/useWithIndex", pattern = pattern, isIgnored = true)
            model("${idea}intentions/joinDeclarationAndAssignment", pattern = pattern, isIgnored = true)
            model("${idea}intentions/moveLambdaInsideParentheses", pattern = pattern, isIgnored = true)
            model("${idea}intentions/convertSealedClassToEnum", pattern = pattern)
            model("${idea}intentions/convertTrimMarginToTrimIndent", pattern = pattern, isIgnored = true)
            model("${idea}intentions/usePropertyAccessSyntax", pattern = pattern, isIgnored = true)
            model("${idea}intentions/convertFunctionTypeParameterToReceiver", pattern = pattern)
            model("${idea}intentions/convertVarargParameterToArray", pattern = pattern, isIgnored = true)
            model("${idea}intentions/removeExplicitLambdaParameterTypes", pattern = pattern, isIgnored = true)
            model("${idea}intentions/convertPrimaryConstructorToSecondary", pattern = pattern)
            model("${idea}intentions/convertArgumentToSet", pattern = pattern, isIgnored = true)
            model("${idea}intentions/addAnnotationUseSiteTarget", pattern = pattern)
            model("${idea}intentions/convertEnumToSealedClass", pattern = pattern)
            model("${idea}intentions/convertToIndexedFunctionCall", pattern = pattern, isIgnored = true)
            model("${idea}intentions/samConversionToAnonymousObject", pattern = pattern, isIgnored = true)
            model("${idea}intentions/convertFunctionTypeReceiverToParameter", pattern = pattern, isIgnored = true)
            model("${idea}intentions/addLabeledReturnInLambda", pattern = pattern)
            model("${idea}intentions/convertFilteringFunctionWithDemorgansLaw", pattern = pattern, isIgnored = true)
            model("${idea}intentions/removeExplicitSuperQualifier", pattern = pattern, isIgnored = true)
            model("${idea}intentions/mergeElseIf", pattern = pattern, isIgnored = true)
            model("${idea}intentions/branched/elvisToIfThen", pattern = pattern)
            model("${idea}intentions/branched/safeAccessToIfThen", pattern = pattern)
            model("${idea}intentions/branched/ifWhen/ifToWhen", pattern = pattern)
            model("${idea}intentions/branched/when/flatten", pattern = pattern)
            model("${idea}intentions/branched/when/eliminateSubject", pattern = pattern)
            model("${idea}intentions/branched/doubleBangToIfThen", pattern = pattern)
            model("${idea}intentions/introduceVariable", pattern = pattern)
            model("${idea}intentions/convertToMultiDollarString", pattern = pattern)
            model("${idea}intentions/branched/unfolding/returnToWhen", pattern = pattern)
            model("${idea}/intentions/concatenationToBuildCollection", pattern = pattern)

            //model("${idea}intentions/loopToCallChain", pattern = pattern, isIgnored = true)
            //model("${idea}intentions/loopToCallChain/forEach", pattern = pattern, isIgnored = true)
            //model("${idea}intentions/loopToCallChain/firstOrNull", pattern = pattern, isIgnored = true)
            //model("${idea}intentions/loopToCallChain/count", pattern = pattern, isIgnored = true)
            //model("${idea}intentions/loopToCallChain/contains", pattern = pattern, isIgnored = true)
            //model("${idea}intentions/loopToCallChain/any", pattern = pattern, isIgnored = true)
            //model("${idea}intentions/loopToCallChain/maxMin", pattern = pattern, isIgnored = true)
            //model("${idea}intentions/loopToCallChain/map", pattern = pattern, isIgnored = true)
            //model("${idea}intentions/loopToCallChain/toCollection", pattern = pattern, isIgnored = true)
            //model("${idea}intentions/loopToCallChain/flatMap", pattern = pattern, isIgnored = true)
            //model("${idea}intentions/loopToCallChain/sum", pattern = pattern, isIgnored = true)
            //model("${idea}intentions/loopToCallChain/takeWhile", pattern = pattern, isIgnored = true)
            //model("${idea}intentions/loopToCallChain/smartCasts", pattern = pattern, isIgnored = true)
            //model("${idea}intentions/loopToCallChain/filter", pattern = pattern, isIgnored = true)
            //model("${idea}intentions/loopToCallChain/introduceIndex", pattern = pattern, isIgnored = true)
            //model("${idea}intentions/loopToCallChain/indexOf", pattern = pattern, isIgnored = true)
            model("${idea}intentions/moveMemberToTopLevel", pattern = pattern)
            model("${idea}intentions/moveOutOfCompanion", pattern = pattern)
            model("${idea}intentions/anonymousFunctionToLambda", pattern = pattern)
            model("${idea}intentions/copyConcatenatedStringToClipboard", pattern = pattern)
            model("${idea}intentions/inlayHints", pattern = pattern, isIgnored = true)
            model("${idea}intentions/convertToScope", pattern = pattern)
            model("${idea}intentions/implementAbstractMember", pattern = pattern)
            model("${idea}intentions/replaceSizeZeroCheckWithIsEmpty", pattern = pattern, isIgnored = true)
            model("${idea}intentions/movePropertyToClassBody", pattern = pattern)
            model("${idea}intentions/indentRawString", pattern = pattern, isIgnored = true)
            model("${idea}intentions/replaceAddWithPlusAssign", pattern = pattern)
            model("${idea}intentions/reconstructTypeInCastOrIs", pattern = pattern, isIgnored = true)
            model("${idea}intentions/convertParameterToReceiver", pattern = pattern)
            model("${idea}intentions/convertCollectionConstructorToFunction", pattern = pattern, isIgnored = true)
            model("${idea}intentions/replaceMapGetOrDefault", pattern = pattern, isIgnored = true)
            model("${idea}intentions/addMissingDestructuring", pattern = pattern, isIgnored = true)
            model("${idea}intentions/convertUnsafeCastToUnsafeCastCall", pattern = pattern, isIgnored = true)
            model("${idea}intentions/moveOutOfCompanion", pattern = pattern, isIgnored = true)
            model("${idea}intentions/moveToCompanion", pattern = pattern)
            model("${idea}intentions/destructuringInLambda", pattern = pattern, isIgnored = true)
            model("${idea}intentions/addThrowsAnnotation", pattern = pattern)
            model("${idea}intentions/replaceItWithExplicitFunctionLiteralParam", pattern = pattern)
            model("${idea}intentions/iterationOverMap", pattern = pattern, isIgnored = true)
            model("${idea}intentions/convertPropertyToFunction", pattern = pattern)
            model("${idea}intentions/convertReceiverToParameter", pattern = pattern)
            model("${idea}intentions/convertUnsafeCastCallToUnsafeCast", pattern = pattern, isIgnored = true)
            model("${idea}intentions/convertSnakeCaseTestFunctionToSpaced", pattern = pattern)
            model("${idea}intentions/addValOrVar", pattern = pattern)
            model("${idea}intentions/convertBlockCommentToLineComment", pattern = pattern)
            model("${idea}intentions/removeSingleExpressionStringTemplate", pattern = pattern, isIgnored = true)
            model("${idea}intentions/convertLambdaToMultiLine", pattern = pattern, isIgnored = true)
            model("${idea}intentions/convertToConcatenatedStringMultiDollarPrefix", pattern = pattern)
            model("${idea}intentions/contextParameters", pattern = pattern)
        }

        testClass<AbstractK2IntentionInInjectionTest> {
            model("code-insight/intentions-k2/tests/testData/intentions/injected", pattern = pattern)
        }

        testClass<AbstractK2MultiFileIntentionTest> {
            model("${idea}/multiFileIntentions/moveDeclarationToSeparateFile", pattern = TEST, flatten = true)
            model("${idea}/multiFileIntentions/implementAbstractMember", pattern = TEST, flatten = true)
            model("${idea}/multiFileIntentions/convertMemberToExtension", pattern = TEST, flatten = true)
            model("${idea}/multiFileIntentions/addJvmStatic", pattern = TEST, flatten = true)
        }

        testClass<AbstractK2GotoTestOrCodeActionTest> {
            model("${idea}navigation/gotoTestOrCode", pattern = Patterns.forRegex("^(.+)\\.main\\..+\$"))
        }
    }

    testGroup("code-insight/intentions-shared/tests/k2", category = INTENTIONS, testDataPath = "../testData") {
        testClass<AbstractSharedK2IntentionTest> {
            model("intentions", pattern = Patterns.forRegex("^([\\w\\-_]+)\\.(kt|kts)$"))
        }
    }
}
