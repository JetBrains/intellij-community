// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.fir.testGenerator

import org.jetbrains.fir.uast.test.*
import org.jetbrains.kotlin.fir.testGenerator.codeinsight.generateK2CodeInsightTests
import org.jetbrains.kotlin.idea.fir.actions.AbstractK2AddImportActionTest
import org.jetbrains.kotlin.idea.fir.actions.AbstractK2BytecodeToolWindowTest
import org.jetbrains.kotlin.idea.fir.analysis.providers.AbstractIdeKotlinAnnotationsResolverTest
import org.jetbrains.kotlin.idea.fir.analysis.providers.dependents.AbstractModuleDependentsTest
import org.jetbrains.kotlin.idea.fir.analysis.providers.sessions.AbstractGlobalSessionInvalidationTest
import org.jetbrains.kotlin.idea.fir.analysis.providers.sessions.AbstractLocalSessionInvalidationTest
import org.jetbrains.kotlin.idea.fir.analysis.providers.trackers.AbstractProjectWideOutOfBlockKotlinModificationTrackerTest
import org.jetbrains.kotlin.idea.fir.codeInsight.AbstractK2MultiModuleLineMarkerTest
import org.jetbrains.kotlin.idea.fir.completion.*
import org.jetbrains.kotlin.idea.fir.completion.test.handlers.AbstractFirKeywordCompletionHandlerTest
import org.jetbrains.kotlin.idea.fir.completion.test.handlers.AbstractHighLevelBasicCompletionHandlerTest
import org.jetbrains.kotlin.idea.fir.completion.test.handlers.AbstractHighLevelJavaCompletionHandlerTest
import org.jetbrains.kotlin.idea.fir.completion.test.handlers.AbstractK2CompletionCharFilterTest
import org.jetbrains.kotlin.idea.fir.completion.wheigher.AbstractHighLevelWeigherTest
import org.jetbrains.kotlin.idea.fir.documentation.AbstractFirQuickDocTest
import org.jetbrains.kotlin.idea.fir.externalAnnotations.AbstractK2ExternalAnnotationTest
import org.jetbrains.kotlin.idea.fir.findUsages.*
import org.jetbrains.kotlin.idea.fir.imports.AbstractFirJvmOptimizeImportsTest
import org.jetbrains.kotlin.idea.fir.low.level.api.AbstractFirLibraryModuleDeclarationResolveTest
import org.jetbrains.kotlin.idea.fir.navigation.AbstractFirGotoDeclarationTest
import org.jetbrains.kotlin.idea.fir.navigation.AbstractFirGotoTypeDeclarationTest
import org.jetbrains.kotlin.idea.fir.parameterInfo.AbstractFirParameterInfoTest
import org.jetbrains.kotlin.idea.fir.quickfix.AbstractHighLevelQuickFixMultiFileTest
import org.jetbrains.kotlin.idea.fir.quickfix.AbstractHighLevelQuickFixMultiModuleTest
import org.jetbrains.kotlin.idea.fir.quickfix.AbstractHighLevelQuickFixTest
import org.jetbrains.kotlin.idea.fir.resolve.*
import org.jetbrains.kotlin.idea.fir.search.AbstractHLImplementationSearcherTest
import org.jetbrains.kotlin.idea.fir.shortenRefs.AbstractFirShortenRefsTest
import org.jetbrains.kotlin.idea.fir.imports.AbstractK2AutoImportTest
import org.jetbrains.kotlin.idea.fir.imports.AbstractK2FilteringAutoImportTest
import org.jetbrains.kotlin.idea.k2.copyright.AbstractFirUpdateKotlinCopyrightTest
import org.jetbrains.kotlin.idea.k2.refactoring.rename.AbstractFirRenameTest
import org.jetbrains.kotlin.idea.k2.refactoring.rename.AbstractK2InplaceRenameTest
import org.jetbrains.kotlin.idea.fir.projectView.AbstractK2ProjectViewTest
import org.jetbrains.kotlin.parcelize.ide.test.AbstractParcelizeK2QuickFixTest
import org.jetbrains.kotlin.testGenerator.generator.TestGenerator
import org.jetbrains.kotlin.testGenerator.model.*
import org.jetbrains.kotlin.testGenerator.model.Patterns.DIRECTORY
import org.jetbrains.kotlin.testGenerator.model.Patterns.JAVA
import org.jetbrains.kotlin.testGenerator.model.Patterns.KT
import org.jetbrains.kotlin.testGenerator.model.Patterns.KT_OR_KTS
import org.jetbrains.kotlin.testGenerator.model.Patterns.KT_OR_KTS_WITHOUT_DOTS
import org.jetbrains.kotlin.testGenerator.model.Patterns.KT_WITHOUT_DOTS
import org.jetbrains.kotlin.testGenerator.model.Patterns.KT_WITHOUT_DOT_AND_FIR_PREFIX
import org.jetbrains.kotlin.testGenerator.model.Patterns.KT_WITHOUT_FIR_PREFIX
import org.jetbrains.kotlin.testGenerator.model.Patterns.TEST
import org.jetbrains.kotlin.testGenerator.model.Patterns.forRegex

fun main(@Suppress("UNUSED_PARAMETER") args: Array<String>) {
    generateK2Tests()
}

fun assembleK2Workspace(): TWorkspace = assembleWorkspace()

fun generateK2Tests(isUpToDateCheck: Boolean = false) {
    System.setProperty("java.awt.headless", "true")
    TestGenerator.write(assembleWorkspace(), isUpToDateCheck)
}

private fun assembleWorkspace(): TWorkspace = workspace {
    generateK2CodeInsightTests()
    generateK2Fe10BindingsTests()
    generateK2NavigationTests()
    generateK2DebuggerTests()
    generateK2HighlighterTests()
    generateK2RefactoringsTests()
    generateK2SearchTests()
    generateK2RefIndexTests()

    testGroup("base/fir/analysis-api-providers") {
        testClass<AbstractProjectWideOutOfBlockKotlinModificationTrackerTest> {
            model("outOfBlockProjectWide", pattern = KT_WITHOUT_DOTS or Patterns.JAVA)
        }

        testClass<AbstractLocalSessionInvalidationTest> {
            model("sessionInvalidation", pattern = DIRECTORY, isRecursive = false)
        }

        testClass<AbstractGlobalSessionInvalidationTest> {
            model("sessionInvalidation", pattern = DIRECTORY, isRecursive = false)
        }

        testClass<AbstractIdeKotlinAnnotationsResolverTest> {
            model("annotationsResolver", pattern = KT_WITHOUT_DOTS)
        }

        testClass<AbstractModuleDependentsTest> {
            model("moduleDependents", pattern = DIRECTORY, isRecursive = false)
        }
    }

    testGroup("compiler-plugins/parcelize/tests/k2", testDataPath = "../testData") {
        testClass<AbstractParcelizeK2QuickFixTest> {
            model("quickfix", pattern = Patterns.forRegex("^([\\w\\-_]+)\\.kt$"))
        }
    }

    testGroup("fir-low-level-api-ide-impl") {
        testClass<AbstractFirLibraryModuleDeclarationResolveTest> {
            model("libraryModuleResolve", isRecursive = false)
        }
    }

    testGroup("fir/tests", testDataPath = "../../idea/tests/testData") {
        testClass<AbstractK2AddImportActionTest> {
            model("idea/actions/kotlinAddImportAction", pattern = KT_WITHOUT_DOTS)
        }

        testClass<AbstractFirReferenceResolveTest> {
            model("resolve/references", pattern = KT_WITHOUT_DOTS)
        }

        testClass<AbstractFirReferenceResolveWithLibTest> {
            model("resolve/referenceWithLib", pattern = DIRECTORY, isRecursive = false)
        }

        testClass<AbstractFirReferenceResolveWithCompiledLibTest> {
            model("resolve/referenceWithLib", pattern = DIRECTORY, isRecursive = false)
        }

        testClass<AbstractFirReferenceResolveWithCrossLibTest> {
            model("resolve/referenceWithLib", pattern = DIRECTORY, isRecursive = false)
        }

        testClass<AbstractReferenceResolveInLibrarySourcesFirTest> {
            model("resolve/referenceInLib", isRecursive = false)
        }

        testClass<AbstractFirReferenceResolveInJavaTest> {
            model("resolve/referenceInJava/binaryAndSource", pattern = JAVA)
            model("resolve/referenceInJava/sourceOnly", pattern = JAVA)
        }

        testClass<AbstractFirReferenceToCompiledKotlinResolveInJavaTest> {
            model("resolve/referenceInJava/binaryAndSource", pattern = JAVA)
        }

        testClass<AbstractFirInLibraryResolveEverythingTest> {
            model("resolve/compiled/sources")
        }

        testClass<AbstractFirGotoTypeDeclarationTest> {
            model("navigation/gotoTypeDeclaration", pattern = TEST)
        }

        testClass<AbstractFirGotoDeclarationTest> {
            model("navigation/gotoDeclaration", pattern = TEST)
        }

        testClass<AbstractHighLevelQuickFixTest> {
            val pattern = Patterns.forRegex("^([\\w\\-_]+)\\.kt$")
            model("quickfix/abstract", pattern = pattern)
            model("quickfix/addExclExclCall", pattern = pattern)
            model("quickfix/addInitializer", pattern = pattern)
            model("quickfix/addPropertyAccessors", pattern = pattern)
            model("quickfix/autoImports", pattern = KT_WITHOUT_DOTS, isRecursive = true)
            model("quickfix/checkArguments", pattern = pattern, isRecursive = false)
            model("quickfix/conflictingImports", pattern = pattern)
            model("quickfix/expressions", pattern = pattern)
            model("quickfix/lateinit", pattern = pattern)
            model("quickfix/localVariableWithTypeParameters", pattern = pattern)
            model("quickfix/modifiers", pattern = pattern, isRecursive = false)
            model("quickfix/modifiers/addOpenToClassDeclaration", pattern = pattern)
            model("quickfix/nullables", pattern = pattern)
            model("quickfix/override", pattern = pattern, isRecursive = false)
            model("quickfix/override/typeMismatchOnOverride", pattern = pattern, isRecursive = false)
            model("quickfix/removeRedundantSpreadOperator", pattern = pattern)
            model("quickfix/replaceInfixOrOperatorCall", pattern = pattern)
            model("quickfix/replaceWithArrayCallInAnnotation", pattern = pattern)
            model("quickfix/replaceWithDotCall", pattern = pattern)
            model("quickfix/replaceWithSafeCall", pattern = pattern)
            model("quickfix/specifyVisibilityInExplicitApiMode", pattern = pattern)
            model("quickfix/supercalls", pattern = pattern)
            model("quickfix/surroundWithArrayOfForNamedArgumentsToVarargs", pattern = pattern)
            model("quickfix/variables/changeMutability", pattern = pattern, isRecursive = false)
            model("quickfix/variables/removeValVarFromParameter", pattern = pattern)
            model("quickfix/when", pattern = pattern)
            model("quickfix/wrapWithSafeLetCall", pattern = pattern)
            model("quickfix/typeAddition", pattern = pattern)
            model("quickfix/typeMismatch/casts", pattern = pattern)
            model("quickfix/typeMismatch/componentFunctionReturnTypeMismatch", pattern = pattern)
            model("quickfix/typeMismatch/parameterTypeMismatch", pattern = pattern)
            model("quickfix/typeMismatch/typeMismatchOnReturnedExpression", pattern = pattern)
            model("quickfix/typeMismatch/wrongPrimitive", pattern = pattern)
            model("quickfix/typeMismatch", isRecursive = false, pattern = pattern)
            model("quickfix/toString", pattern = pattern)
            model("quickfix/specifySuperType", pattern = pattern)
            model("quickfix/convertToBlockBody", pattern = pattern)
            model("quickfix/supertypeInitialization", pattern = pattern)

            model("quickfix/addAnnotationTarget", pattern = pattern, isIgnored = true)
            model("quickfix/addAnnotationUseSiteTarget", pattern = pattern, isIgnored = true)
            model("quickfix/addConstructorParameter", pattern = pattern, isIgnored = true)
            model("quickfix/addConstructorParameterFromSuperTypeCall", pattern = pattern, isIgnored = true)
            model("quickfix/addConversionCall", pattern = pattern, isIgnored = true)
            model("quickfix/addCrossinline", pattern = pattern, isIgnored = true)
            model("quickfix/addDataModifier", pattern = pattern, isIgnored = true)
            model("quickfix/addDefaultConstructor", pattern = pattern, isIgnored = true)
            model("quickfix/addElseBranchToIf", pattern = pattern, isIgnored = true)
            model("quickfix/addEmptyArgumentList", pattern = pattern, isIgnored = true)
            model("quickfix/addEqEqTrue", pattern = pattern, isIgnored = true)
            model("quickfix/addFunModifier", pattern = pattern, isIgnored = true)
            model("quickfix/addGenericUpperBound", pattern = pattern, isIgnored = true)
            model("quickfix/addInline", pattern = pattern, isIgnored = true)
            model("quickfix/addInlineToReifiedFunctionFix", pattern = pattern, isIgnored = true)
            model("quickfix/addIsToWhenCondition", pattern = pattern, isIgnored = true)
            model("quickfix/addJvmInline", pattern = pattern, isIgnored = true)
            model("quickfix/addJvmStaticAnnotation", pattern = pattern, isIgnored = true)
            model("quickfix/addNewLineAfterAnnotations", pattern = pattern, isIgnored = true)
            model("quickfix/addNoinline", pattern = pattern, isIgnored = true)
            model("quickfix/addReifiedToTypeParameterOfFunctionFix", pattern = pattern, isIgnored = true)
            model("quickfix/addReturnExpression", pattern = pattern, isIgnored = true)
            model("quickfix/addReturnToLastExpressionInFunction", pattern = pattern, isIgnored = true)
            model("quickfix/addReturnToUnusedLastExpressionInFunction", pattern = pattern, isIgnored = true)
            model("quickfix/addRunBeforeLambda", pattern = pattern, isIgnored = true)
            model("quickfix/addSemicolonBeforeLambdaExpression", pattern = pattern, isIgnored = true)
            model("quickfix/addSpreadOperatorForArrayAsVarargAfterSam", pattern = pattern, isIgnored = true)
            model("quickfix/addStarProjections", pattern = pattern, isIgnored = true)
            model("quickfix/addSuspend", pattern = pattern, isIgnored = true)
            model("quickfix/addTypeAnnotationToValueParameter", pattern = pattern, isIgnored = true)
            model("quickfix/addUnsafeVarianceAnnotation", pattern = pattern, isIgnored = true)
            model("quickfix/addValVar", pattern = pattern, isIgnored = true)
            model("quickfix/addVarianceModifier", pattern = pattern, isIgnored = true)
            model("quickfix/assignToProperty", pattern = pattern, isIgnored = true)
            model("quickfix/callFromPublicInline", pattern = pattern, isIgnored = true)
            model("quickfix/canBeParameter", pattern = pattern, isIgnored = true)
            model("quickfix/canBePrimaryConstructorProperty", pattern = pattern, isIgnored = true)
            model("quickfix/castDueToProgressionResolveChange", pattern = pattern, isIgnored = true)
            model("quickfix/changeObjectToClass", pattern = pattern, isIgnored = true)
            model("quickfix/changeSignature", pattern = pattern, isIgnored = true)
            model("quickfix/changeSuperTypeListEntryTypeArgument", pattern = pattern, isIgnored = true)
            model("quickfix/changeToLabeledReturn", pattern = pattern, isIgnored = true)
            model("quickfix/changeToMutableCollection", pattern = pattern, isIgnored = true)
            model("quickfix/changeToUseSpreadOperator", pattern = pattern, isIgnored = true)
            model("quickfix/compilerError", pattern = pattern, isIgnored = true)
            model("quickfix/convertCollectionLiteralToIntArrayOf", pattern = pattern, isIgnored = true)
            model("quickfix/convertIllegalEscapeToUnicodeEscape", pattern = pattern, isIgnored = true)
            model("quickfix/convertJavaInterfaceToClass", pattern = pattern, isIgnored = true)
            model("quickfix/convertLateinitPropertyToNotNullDelegate", pattern = pattern, isIgnored = true)
            model("quickfix/convertPropertyInitializerToGetter", pattern = pattern, isIgnored = true)
            model("quickfix/convertToAnonymousObject", pattern = pattern, isIgnored = true)
            model("quickfix/convertToIsArrayOfCall", pattern = pattern, isIgnored = true)
            model("quickfix/createFromUsage", pattern = pattern, isIgnored = true)
            model("quickfix/createLabel", pattern = pattern, isIgnored = true)
            model("quickfix/declarationCantBeInlined", pattern = pattern, isIgnored = true)
            model("quickfix/declaringJavaClass", pattern = pattern, isIgnored = true)
            model("quickfix/decreaseVisibility", pattern = pattern, isIgnored = true)
            model("quickfix/deprecatedJavaAnnotation", pattern = pattern, isIgnored = true)
            model("quickfix/deprecatedSymbolUsage", pattern = pattern, isIgnored = true)
            model("quickfix/equalityNotApplicable", pattern = pattern, isIgnored = true)
            model("quickfix/final", pattern = pattern, isIgnored = true)
            model("quickfix/foldTryCatch", pattern = pattern, isIgnored = true)
            model("quickfix/functionWithLambdaExpressionBody", pattern = pattern, isIgnored = true)
            model("quickfix/implement", pattern = pattern, isIgnored = true)
            model("quickfix/importAlias", pattern = pattern, isIgnored = true)
            model("quickfix/increaseVisibility", pattern = pattern, isIgnored = true)
            model("quickfix/initializeWithConstructorParameter", pattern = pattern, isIgnored = true)
            model("quickfix/inlineClass", pattern = pattern, isIgnored = true)
            model("quickfix/inlineTypeParameterFix", pattern = pattern, isIgnored = true)
            model("quickfix/insertDelegationCall", pattern = pattern, isIgnored = true)
            model("quickfix/isEnumEntry", pattern = pattern, isIgnored = true)
            model("quickfix/javaClassOnCompanion", pattern = pattern, isIgnored = true)
            model("quickfix/kdocMissingDocumentation", pattern = pattern, isIgnored = true)
            model("quickfix/leakingThis", pattern = pattern, isIgnored = true)
            model("quickfix/libraries", pattern = pattern, isIgnored = true)
            model("quickfix/makeConstructorParameterProperty", pattern = pattern, isIgnored = true)
            model("quickfix/makePrivateAndOverrideMember", pattern = pattern, isIgnored = true)
            model("quickfix/makeTypeParameterReified", pattern = pattern, isIgnored = true)
            model("quickfix/makeUpperBoundNonNullable", pattern = pattern, isIgnored = true)
            model("quickfix/memberVisibilityCanBePrivate", pattern = pattern, isIgnored = true)
            model("quickfix/migration", pattern = pattern, isIgnored = true)
            model("quickfix/missingConstructorBrackets", pattern = pattern, isIgnored = true)
            model("quickfix/moveMemberToCompanionObject", pattern = pattern, isIgnored = true)
            model("quickfix/moveReceiverAnnotation", pattern = pattern, isIgnored = true)
            model("quickfix/moveToConstructorParameters", pattern = pattern, isIgnored = true)
            model("quickfix/moveToSealedParent", pattern = pattern, isIgnored = true)
            model("quickfix/moveTypeAliasToTopLevel", pattern = pattern, isIgnored = true)
            model("quickfix/obsoleteKotlinJsPackages", pattern = pattern, isIgnored = true)
            model("quickfix/optIn", pattern = pattern, isIgnored = true)
            model("quickfix/optimizeImports", pattern = pattern, isIgnored = true)
            model("quickfix/platformClasses", pattern = pattern, isIgnored = true)
            model("quickfix/platformTypesInspection", pattern = pattern, isIgnored = true)
            model("quickfix/primitiveCastToConversion", pattern = pattern, isIgnored = true)
            model("quickfix/properties", pattern = pattern, isIgnored = true)
            model("quickfix/protectedInFinal", pattern = pattern, isIgnored = true)
            model("quickfix/redundantConst", pattern = pattern, isIgnored = true)
            model("quickfix/redundantFun", pattern = pattern, isIgnored = true)
            model("quickfix/redundantIf", pattern = pattern, isIgnored = true)
            model("quickfix/redundantInline", pattern = pattern, isIgnored = true)
            model("quickfix/redundantLateinit", pattern = pattern, isIgnored = true)
            model("quickfix/redundantModalityModifier", pattern = pattern, isIgnored = true)
            model("quickfix/redundantSuspend", pattern = pattern, isIgnored = true)
            model("quickfix/redundantVisibilityModifier", pattern = pattern, isIgnored = true)
            model("quickfix/removeAnnotation", pattern = pattern, isIgnored = true)
            model("quickfix/removeArgument", pattern = pattern, isIgnored = true)
            model("quickfix/removeAtFromAnnotationArgument", pattern = pattern, isIgnored = true)
            model("quickfix/removeDefaultParameterValue", pattern = pattern, isIgnored = true)
            model("quickfix/removeFinalUpperBound", pattern = pattern, isIgnored = true)
            model("quickfix/removeNoConstructor", pattern = pattern, isIgnored = true)
            model("quickfix/removeRedundantAssignment", pattern = pattern, isIgnored = true)
            model("quickfix/removeRedundantInitializer", pattern = pattern, isIgnored = true)
            model("quickfix/removeRedundantLabel", pattern = pattern, isIgnored = true)
            model("quickfix/removeSingleLambdaParameter", pattern = pattern, isIgnored = true)
            model("quickfix/removeSuspend", pattern = pattern, isIgnored = true)
            model("quickfix/removeToStringInStringTemplate", pattern = pattern, isIgnored = true)
            model("quickfix/removeTypeVariance", pattern = pattern, isIgnored = true)
            model("quickfix/removeUnused", pattern = pattern, isIgnored = true)
            model("quickfix/removeUnusedParameter", pattern = pattern, isIgnored = true)
            model("quickfix/removeUnusedReceiver", pattern = pattern, isIgnored = true)
            model("quickfix/removeUseSiteTarget", pattern = pattern, isIgnored = true)
            model("quickfix/renameToRem", pattern = pattern, isIgnored = true)
            model("quickfix/renameToUnderscore", pattern = pattern, isIgnored = true)
            model("quickfix/renameUnresolvedReference", pattern = pattern, isIgnored = true)
            model("quickfix/reorderParameters", pattern = pattern, isIgnored = true)
            model("quickfix/replaceJvmFieldWithConst", pattern = pattern, isIgnored = true)
            model("quickfix/restrictedRetentionForExpressionAnnotation", pattern = pattern, isIgnored = true)
            model("quickfix/simplifyComparison", pattern = pattern, isIgnored = true)
            model("quickfix/smartCastImpossibleInIfThen", pattern = pattern, isIgnored = true)
            model("quickfix/specifyOverrideExplicitly", pattern = pattern, isIgnored = true)
            model("quickfix/specifySuperExplicitly", pattern = pattern, isIgnored = true)
            model("quickfix/specifyTypeExplicitly", pattern = pattern, isIgnored = true)
            model("quickfix/superTypeIsExtensionType", pattern = pattern, isIgnored = true)
            model("quickfix/suppress", pattern = pattern, isIgnored = true)
            model("quickfix/surroundWithNullCheck", pattern = pattern, isIgnored = true)
            model("quickfix/suspiciousCollectionReassignment", pattern = pattern, isIgnored = true)
            model("quickfix/tooLongCharLiteralToString", pattern = pattern, isIgnored = true)
            model("quickfix/typeImports", pattern = pattern, isIgnored = true)
            model("quickfix/typeInferenceExpectedTypeMismatch", pattern = pattern, isIgnored = true)
            model("quickfix/typeOfAnnotationMember", pattern = pattern, isIgnored = true)
            model("quickfix/typeParameters", pattern = pattern, isIgnored = true)
            model("quickfix/typeProjection", pattern = pattern, isIgnored = true)
            model("quickfix/unnecessaryLateinit", pattern = pattern, isIgnored = true)
            model("quickfix/unusedSuppressAnnotation", pattern = pattern, isIgnored = true)
            model("quickfix/useFullyqualifiedCall", pattern = pattern, isIgnored = true)
            model("quickfix/variables", pattern = pattern, isRecursive = false, isIgnored = true)
            model("quickfix/variables/changeToPropertyAccess", pattern = pattern, isRecursive = false, isIgnored = true)
            model("quickfix/variables/changeToFunctionInvocation", pattern = pattern, isRecursive = false, isIgnored = true)
            model("quickfix/wrapArgumentWithParentheses", pattern = pattern, isIgnored = true)
            model("quickfix/wrapWhenExpressionInParentheses", pattern = pattern, isIgnored = true)
            model("quickfix/wrongLongSuffix", pattern = pattern, isIgnored = true)
            model("quickfix/yieldUnsupported", pattern = pattern, isIgnored = true)
        }

        testClass<AbstractHighLevelQuickFixMultiFileTest> {
            model(
                "quickfix/autoImports",
                pattern = Patterns.forRegex("""^(\w+)\.((before\.Main\.\w+)|(test))$"""),
                testMethodName = "doTestWithExtraFile"
            )
        }

        testClass<AbstractHighLevelQuickFixMultiModuleTest> {
            model("multiModuleQuickFix", pattern = DIRECTORY, depth = 1)
        }

        testClass<AbstractFirShortenRefsTest> {
            model("shortenRefsFir", pattern = KT_WITHOUT_DOTS, testMethodName = "doTestWithMuting")
            model("shortenRefs/this", pattern = KT_WITHOUT_DOTS, testMethodName = "doTestWithMuting")
        }

        testClass<AbstractFirParameterInfoTest> {
            model(
                "parameterInfo", pattern = Patterns.forRegex("^([\\w\\-_]+)\\.kt$"), isRecursive = true,
                excludedDirectories = listOf("withLib1/sharedLib", "withLib2/sharedLib", "withLib3/sharedLib")
            )
        }

        testClass<AbstractK2AutoImportTest> {
            model(
                "editor/autoImport", testMethodName = "doTest", testClassName = "WithAutoImport",
                pattern = DIRECTORY, isRecursive = false
            )
        }

        testClass<AbstractK2FilteringAutoImportTest> {
            model(
                "editor/autoImportExtension", testMethodName = "doTest", testClassName = "WithAutoImport",
                pattern = DIRECTORY, isRecursive = false
            )
        }

        testClass<AbstractFirJvmOptimizeImportsTest> {
            model("editor/optimizeImports/jvm", pattern = KT_WITHOUT_DOTS)
            model("editor/optimizeImports/common", pattern = KT_WITHOUT_DOTS)
        }

        testClass<AbstractK2BytecodeToolWindowTest> {
            model("internal/toolWindow", isRecursive = false, pattern = DIRECTORY)
        }

        testClass<AbstractK2ExternalAnnotationTest> {
            model("externalAnnotations", pattern = KT_WITHOUT_DOTS)
        }
    }

    testGroup("fir/tests", testDataPath = "../../completion/testData") {
        testClass<AbstractK2JvmBasicCompletionTest> {
            model("basic/common", pattern = KT_WITHOUT_FIR_PREFIX)
            model("basic/java", pattern = KT_WITHOUT_FIR_PREFIX)
            model("../../idea-fir/testData/completion/basic/common", testClassName = "CommonFir")
        }

        testClass<AbstractK2JvmBasicCompletionTest>("org.jetbrains.kotlin.idea.fir.completion.K2KDocCompletionTestGenerated") {
            model("kdoc", pattern = KT_WITHOUT_FIR_PREFIX)
        }

        testClass<AbstractHighLevelBasicCompletionHandlerTest> {
            model("handlers/basic", pattern = KT_WITHOUT_DOT_AND_FIR_PREFIX)
            model("handlers", pattern = KT_WITHOUT_DOT_AND_FIR_PREFIX, isRecursive = false)
        }

        testClass<AbstractHighLevelJavaCompletionHandlerTest> {
            model("handlers/injava", pattern = Patterns.JAVA)
        }

        testClass<AbstractFirKeywordCompletionHandlerTest> {
            model("handlers/keywords", pattern = KT_WITHOUT_DOT_AND_FIR_PREFIX)
        }

        testClass<AbstractHighLevelWeigherTest> {
            model("weighers/basic", pattern = KT_OR_KTS_WITHOUT_DOTS)
        }

        testClass<AbstractHighLevelMultiFileJvmBasicCompletionTest> {
            model("basic/multifile", pattern = DIRECTORY, isRecursive = false)
        }

        testClass<AbstractK2MultiPlatformCompletionTest> {
            model("multiPlatform", isRecursive = false, pattern = DIRECTORY)
        }

        testClass<AbstractK2CompletionCharFilterTest> {
            model("handlers/charFilter", pattern = KT_WITHOUT_DOT_AND_FIR_PREFIX)
        }

        testClass<AbstractFirKeywordCompletionTest> {
            model("keywords", isRecursive = false, pattern = KT_WITHOUT_FIR_PREFIX)
            model(
                "../../idea-fir/testData/completion/keywords",
                testClassName = "KeywordsFir",
                isRecursive = false,
                pattern = KT_WITHOUT_FIR_PREFIX
            )
        }
        testClass<AbstractFirWithLibBasicCompletionTest> {
            model("basic/withLib", isRecursive = false, pattern = KT_WITHOUT_FIR_PREFIX)
        }

        testClass<AbstractFirWithMppStdlibCompletionTest> {
            model("basic/stdlibWithCommon", isRecursive = false, pattern = KT_WITHOUT_FIR_PREFIX)
        }
    }

    testGroup("fir/tests", testDataPath = "../../code-insight/testData") {
        testClass<AbstractK2MultiModuleLineMarkerTest> {
            model("linemarkers", isRecursive = false, pattern = DIRECTORY)
        }
    }

    testGroup("fir/tests", testDataPath = "../../idea/tests/testData") {
        testClass<AbstractK2ProjectViewTest> {
            model("projectView", pattern = TEST)
        }
    }

    testGroup("refactorings/rename.k2", testDataPath = "../../idea/tests/testData") {
        testClass<AbstractFirRenameTest> {
            model("refactoring/rename", pattern = TEST, flatten = true)
        }
        testClass<AbstractK2InplaceRenameTest> {
            model("refactoring/rename/inplace", pattern = KT, flatten = true)
        }
    }

    testGroup("fir/tests", testDataPath = "../../idea/tests/testData/findUsages") {
        testClass<AbstractFindUsagesFirTest> {
            model("kotlin", pattern = Patterns.forRegex("""^(.+)\.0\.(kt|kts)$"""))
            model("java", pattern = Patterns.forRegex("""^(.+)\.0\.java$"""))
            model("propertyFiles", pattern = Patterns.forRegex("""^(.+)\.0\.properties$"""))
        }

        testClass<AbstractFindUsagesWithDisableComponentSearchFirTest> {
            model("kotlin/conventions/components", pattern = Patterns.forRegex("""^(.+)\.0\.(kt|kts)$"""))
        }

        testClass<AbstractKotlinFindUsagesWithLibraryFirTest> {
            model("libraryUsages", pattern = Patterns.forRegex("""^(.+)\.0\.(kt|java)$"""))
        }

        testClass<AbstractKotlinFindUsagesWithStdlibFirTest> {
            model("stdlibUsages", pattern = Patterns.forRegex("""^(.+)\.0\.kt$"""))
        }

        testClass<AbstractKotlinGroupUsagesBySimilarityFirTest> {
            model("similarity/grouping", pattern = KT)
        }

        testClass<AbstractKotlinGroupUsagesBySimilarityFeaturesFirTest> {
            model("similarity/features", pattern = KT)
        }

        testClass<AbstractKotlinScriptFindUsagesFirTest> {
            model("kotlinScript", pattern = Patterns.forRegex("""^(.+)\.0\.kts$"""))
        }
    }

    testGroup("fir/tests") {
        testClass<AbstractHLImplementationSearcherTest> {
            model("search/implementations", pattern = KT_WITHOUT_DOTS)
        }

        testClass<AbstractFirQuickDocTest> {
            model("quickDoc", pattern = Patterns.forRegex("""^([^_]+)\.(kt|java)$"""))
        }

        testClass<AbstractK2MultiModuleHighlightingTest> {
            model("resolve/anchors", isRecursive = false, pattern = forRegex("^([^\\._]+)$"))
        }

        testClass<AbstractK2ReferenceResolveWithResolveExtensionTest> {
            model("extensions/references", pattern = KT_WITHOUT_DOTS)
        }

        testClass<AbstractK2JvmBasicCompletionTestWithResolveExtension> {
            model("extensions/completion", pattern = KT_WITHOUT_DOTS)
        }

        testClass<AbstractAdditionalKDocResolutionProviderTest> {
            model("resolve/additionalKDocReference", pattern = KT_WITHOUT_DOTS)
        }
    }

    testGroup("uast/uast-kotlin-fir/tests") {
        testClass<AbstractFirUastDeclarationTest> {
            model("declaration", pattern = KT_OR_KTS)
        }

        testClass<AbstractFirUastTypesTest> {
            model("type")
        }

        testClass<AbstractFirUastValuesTest> {
            model("value")
        }
    }

    testGroup("uast/uast-kotlin-fir/tests", testDataPath = "../../uast-kotlin/tests/testData") {
        testClass<AbstractFirLegacyUastDeclarationTest> {
            model("")
        }

        testClass<AbstractFirLegacyUastIdentifiersTest> {
            model("")
        }

        testClass<AbstractFirLegacyUastResolveEverythingTest> {
            model("")
        }

        testClass<AbstractFirLegacyUastTypesTest> {
            model("")
        }

        testClass<AbstractFirLegacyUastValuesTest> {
            model("")
        }
    }

    testGroup("copyright/fir-tests", testDataPath = "../../copyright/tests/testData") {
        testClass<AbstractFirUpdateKotlinCopyrightTest> {
            model("update", pattern = KT_OR_KTS, testMethodName = "doTest")
        }
    }
}
