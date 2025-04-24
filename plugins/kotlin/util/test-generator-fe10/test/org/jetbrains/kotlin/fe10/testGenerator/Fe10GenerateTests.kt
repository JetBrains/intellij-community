// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.fe10.testGenerator

import com.intellij.testFramework.TestIndexingModeSupporter.IndexingMode
import org.jetbrains.kotlin.AbstractDataFlowValueRenderingTest
import org.jetbrains.kotlin.addImport.AbstractAddImportTest
import org.jetbrains.kotlin.addImportAlias.AbstractAddImportAliasTest53
import org.jetbrains.kotlin.asJava.classes.AbstractIdeCompiledLightClassesByFqNameTest
import org.jetbrains.kotlin.asJava.classes.AbstractIdeLightClassesByFqNameTest
import org.jetbrains.kotlin.asJava.classes.AbstractIdeLightClassesByPsiTest
import org.jetbrains.kotlin.checkers.*
import org.jetbrains.kotlin.copyright.AbstractUpdateKotlinCopyrightTest
import org.jetbrains.kotlin.findUsages.*
import org.jetbrains.kotlin.formatter.AbstractEnterHandlerTest
import org.jetbrains.kotlin.formatter.AbstractFormatterTest
import org.jetbrains.kotlin.idea.AbstractExpressionSelectionTest
import org.jetbrains.kotlin.idea.AbstractSmartSelectionTest
import org.jetbrains.kotlin.idea.AbstractWorkSelectionTest
import org.jetbrains.kotlin.idea.actions.AbstractGotoTestOrCodeActionTest
import org.jetbrains.kotlin.idea.actions.AbstractK1AddImportActionTest
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts
import org.jetbrains.kotlin.idea.caches.resolve.AbstractMultiModuleLineMarkerTest
import org.jetbrains.kotlin.idea.caches.resolve.AbstractMultiPlatformHighlightingTest
import org.jetbrains.kotlin.idea.caches.resolve.AbstractMultiplatformAnalysisTest
import org.jetbrains.kotlin.idea.codeInsight.*
import org.jetbrains.kotlin.idea.codeInsight.generate.AbstractCodeInsightActionTest
import org.jetbrains.kotlin.idea.codeInsight.generate.AbstractGenerateHashCodeAndEqualsActionTest
import org.jetbrains.kotlin.idea.codeInsight.generate.AbstractGenerateTestSupportMethodActionTest
import org.jetbrains.kotlin.idea.codeInsight.generate.AbstractGenerateToStringActionTest
import org.jetbrains.kotlin.idea.codeInsight.gradle.AbstractGradleBuildFileHighlightingTest
import org.jetbrains.kotlin.idea.codeInsight.hints.*
import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.AbstractSharedK1InspectionTest
import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.AbstractSharedK1LocalInspectionTest
import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.idea.kdoc.AbstractSharedK1KDocHighlightingTest
import org.jetbrains.kotlin.idea.codeInsight.intentions.shared.AbstractSharedK1IntentionTest
import org.jetbrains.kotlin.idea.codeInsight.moveUpDown.AbstractMoveLeftRightTest
import org.jetbrains.kotlin.idea.codeInsight.moveUpDown.AbstractMoveStatementTest
import org.jetbrains.kotlin.idea.codeInsight.postfix.AbstractK1PostfixTemplateTest
import org.jetbrains.kotlin.idea.codeInsight.surroundWith.AbstractSurroundWithTest
import org.jetbrains.kotlin.idea.codeInsight.unwrap.AbstractUnwrapRemoveTest
import org.jetbrains.kotlin.idea.codeMetaInfo.AbstractMultiModuleLineMarkerCodeMetaInfoTest
import org.jetbrains.kotlin.idea.compilerPlugin.kotlinxSerialization.AbstractSerializationPluginIdeDiagnosticTest
import org.jetbrains.kotlin.idea.compilerPlugin.kotlinxSerialization.AbstractSerializationQuickFixTest
import org.jetbrains.kotlin.idea.completion.test.*
import org.jetbrains.kotlin.idea.completion.test.handlers.*
import org.jetbrains.kotlin.idea.completion.test.weighers.AbstractBasicCompletionWeigherTest
import org.jetbrains.kotlin.idea.completion.test.weighers.AbstractSmartCompletionWeigherTest
import org.jetbrains.kotlin.idea.configuration.gradle.AbstractGradleConfigureProjectByChangingFileTest
import org.jetbrains.kotlin.idea.conversion.copy.AbstractLiteralKotlinToKotlinCopyPasteTest
import org.jetbrains.kotlin.idea.conversion.copy.AbstractLiteralTextToKotlinCopyPasteTest
import org.jetbrains.kotlin.idea.coverage.AbstractKotlinCoverageOutputFilesTest
import org.jetbrains.kotlin.idea.debugger.evaluate.AbstractK1CodeFragmentAutoImportTest
import org.jetbrains.kotlin.idea.debugger.evaluate.AbstractK1CodeFragmentCompletionHandlerTest
import org.jetbrains.kotlin.idea.debugger.evaluate.AbstractK1CodeFragmentCompletionTest
import org.jetbrains.kotlin.idea.debugger.evaluate.AbstractK1CodeFragmentHighlightingTest
import org.jetbrains.kotlin.idea.debugger.test.*
import org.jetbrains.kotlin.idea.debugger.test.sequence.exec.AbstractIrSequenceTraceTestCase
import org.jetbrains.kotlin.idea.decompiler.navigation.*
import org.jetbrains.kotlin.idea.decompiler.stubBuilder.AbstractLoadJavaClsStubTest
import org.jetbrains.kotlin.idea.decompiler.textBuilder.AbstractCommonDecompiledTextTest
import org.jetbrains.kotlin.idea.decompiler.textBuilder.AbstractJvmDecompiledTextTest
import org.jetbrains.kotlin.idea.editor.backspaceHandler.AbstractBackspaceHandlerTest
import org.jetbrains.kotlin.idea.editor.commenter.AbstractKotlinCommenterTest
import org.jetbrains.kotlin.idea.editor.quickDoc.AbstractQuickDocProviderTest
import org.jetbrains.kotlin.idea.externalAnnotations.AbstractK1ExternalAnnotationTest
import org.jetbrains.kotlin.idea.folding.AbstractKotlinFoldingTest
import org.jetbrains.kotlin.idea.hierarchy.AbstractHierarchyTest
import org.jetbrains.kotlin.idea.hierarchy.AbstractHierarchyWithLibTest
import org.jetbrains.kotlin.idea.highlighter.*
import org.jetbrains.kotlin.idea.imports.AbstractJsOptimizeImportsTest
import org.jetbrains.kotlin.idea.imports.AbstractJvmOptimizeImportsTest
import org.jetbrains.kotlin.idea.imports.AbstractK1AutoImportTest
import org.jetbrains.kotlin.idea.imports.AbstractK1FilteringAutoImportTest
import org.jetbrains.kotlin.idea.index.AbstractKotlinTypeAliasByExpansionShortNameIndexTest
import org.jetbrains.kotlin.idea.inspections.AbstractInspectionTest
import org.jetbrains.kotlin.idea.inspections.AbstractLocalInspectionTest
import org.jetbrains.kotlin.idea.inspections.AbstractMultiFileLocalInspectionTest
import org.jetbrains.kotlin.idea.inspections.AbstractViewOfflineInspectionTest
import org.jetbrains.kotlin.idea.intentions.AbstractConcatenatedStringGeneratorTest
import org.jetbrains.kotlin.idea.intentions.AbstractK1IntentionTest
import org.jetbrains.kotlin.idea.intentions.AbstractK1IntentionTest2
import org.jetbrains.kotlin.idea.intentions.AbstractK1MultiFileIntentionTest
import org.jetbrains.kotlin.idea.intentions.declarations.AbstractJoinLinesTest
import org.jetbrains.kotlin.idea.internal.AbstractBytecodeToolWindowMultiplatformTest
import org.jetbrains.kotlin.idea.internal.AbstractBytecodeToolWindowTest
import org.jetbrains.kotlin.idea.kdoc.AbstractKDocHighlightingTest
import org.jetbrains.kotlin.idea.kdoc.AbstractKDocTypingTest
import org.jetbrains.kotlin.idea.maven.AbstractKotlinMavenInspectionTest
import org.jetbrains.kotlin.idea.maven.configuration.AbstractMavenConfigureProjectByChangingFileTest
import org.jetbrains.kotlin.idea.navigation.*
import org.jetbrains.kotlin.idea.navigationToolbar.AbstractKotlinNavBarTest
import org.jetbrains.kotlin.idea.parameterInfo.AbstractParameterInfoTest
import org.jetbrains.kotlin.idea.perf.stats.AbstractPerformanceBasicCompletionHandlerStatNamesTest
import org.jetbrains.kotlin.idea.perf.stats.AbstractPerformanceHighlightingStatNamesTest
import org.jetbrains.kotlin.idea.perf.synthetic.*
import org.jetbrains.kotlin.idea.projectView.AbstractKotlinProjectViewTest
import org.jetbrains.kotlin.idea.quickfix.AbstractK1QuickFixTest
import org.jetbrains.kotlin.idea.quickfix.AbstractQuickFixMultiFileTest
import org.jetbrains.kotlin.idea.quickfix.AbstractQuickFixMultiModuleTest
import org.jetbrains.kotlin.idea.quickfix.AbstractSharedK1QuickFixTest
import org.jetbrains.kotlin.idea.refactoring.AbstractNameSuggestionProviderTest
import org.jetbrains.kotlin.idea.refactoring.copy.AbstractCopyTest
import org.jetbrains.kotlin.idea.refactoring.copy.AbstractMultiModuleCopyTest
import org.jetbrains.kotlin.idea.refactoring.inline.AbstractInlineMultiFileTest
import org.jetbrains.kotlin.idea.refactoring.inline.AbstractInlineTest
import org.jetbrains.kotlin.idea.refactoring.inline.AbstractInlineTestWithSomeDescriptors
import org.jetbrains.kotlin.idea.refactoring.introduce.AbstractExtractionTest
import org.jetbrains.kotlin.idea.refactoring.introduce.AbstractInplaceIntroduceFunctionTest
import org.jetbrains.kotlin.idea.refactoring.move.AbstractK1MultiModuleMoveTest
import org.jetbrains.kotlin.idea.refactoring.move.AbstractMoveTest
import org.jetbrains.kotlin.idea.refactoring.pullUp.AbstractPullUpTest
import org.jetbrains.kotlin.idea.refactoring.pushDown.AbstractPushDownTest
import org.jetbrains.kotlin.idea.refactoring.rename.AbstractInplaceRenameTest
import org.jetbrains.kotlin.idea.refactoring.rename.AbstractMultiModuleRenameTest
import org.jetbrains.kotlin.idea.refactoring.rename.AbstractRenameTest
import org.jetbrains.kotlin.idea.refactoring.safeDelete.AbstractMultiModuleSafeDeleteTest
import org.jetbrains.kotlin.idea.refactoring.safeDelete.AbstractSafeDeleteTest
import org.jetbrains.kotlin.idea.repl.AbstractIdeReplCompletionTest
import org.jetbrains.kotlin.idea.resolve.*
import org.jetbrains.kotlin.idea.scratch.AbstractScratchLineMarkersTest
import org.jetbrains.kotlin.idea.script.*
import org.jetbrains.kotlin.idea.search.refIndex.AbstractFindUsagesWithCompilerReferenceIndexTest
import org.jetbrains.kotlin.idea.search.refIndex.AbstractKotlinCompilerReferenceByReferenceTest
import org.jetbrains.kotlin.idea.search.refIndex.AbstractKotlinCompilerReferenceTest
import org.jetbrains.kotlin.idea.slicer.AbstractSlicerLeafGroupingTest
import org.jetbrains.kotlin.idea.slicer.AbstractSlicerMultiplatformTest
import org.jetbrains.kotlin.idea.slicer.AbstractSlicerNullnessGroupingTest
import org.jetbrains.kotlin.idea.slicer.AbstractSlicerTreeTest
import org.jetbrains.kotlin.idea.structureView.AbstractKotlinFileStructureTest
import org.jetbrains.kotlin.idea.stubs.AbstractMultiFileHighlightingTest
import org.jetbrains.kotlin.idea.stubs.AbstractResolveByStubTest
import org.jetbrains.kotlin.idea.stubs.AbstractStubBuilderTest
import org.jetbrains.kotlin.nj2k.*
import org.jetbrains.kotlin.nj2k.inference.common.AbstractCommonConstraintCollectorTest
import org.jetbrains.kotlin.nj2k.inference.mutability.AbstractMutabilityInferenceTest
import org.jetbrains.kotlin.nj2k.inference.nullability.AbstractNullabilityInferenceTest
import org.jetbrains.kotlin.parcelize.ide.test.AbstractParcelizeK1CheckerTest
import org.jetbrains.kotlin.parcelize.ide.test.AbstractParcelizeK1QuickFixTest
import org.jetbrains.kotlin.psi.patternMatching.AbstractK1PsiUnifierTest
import org.jetbrains.kotlin.search.AbstractAnnotatedMembersSearchTest
import org.jetbrains.kotlin.search.AbstractInheritorsSearchTest
import org.jetbrains.kotlin.shortenRefs.AbstractShortenRefsTest
import org.jetbrains.kotlin.test.TargetBackend
import org.jetbrains.kotlin.testGenerator.generator.TestGenerator
import org.jetbrains.kotlin.testGenerator.model.*
import org.jetbrains.kotlin.testGenerator.model.GroupCategory.*
import org.jetbrains.kotlin.testGenerator.model.Patterns.DIRECTORY
import org.jetbrains.kotlin.testGenerator.model.Patterns.JAVA
import org.jetbrains.kotlin.testGenerator.model.Patterns.KT
import org.jetbrains.kotlin.testGenerator.model.Patterns.KTS
import org.jetbrains.kotlin.testGenerator.model.Patterns.KT_OR_JAVA
import org.jetbrains.kotlin.testGenerator.model.Patterns.KT_OR_KTS
import org.jetbrains.kotlin.testGenerator.model.Patterns.KT_OR_KTS_WITHOUT_DOTS
import org.jetbrains.kotlin.testGenerator.model.Patterns.KT_WITHOUT_DOTS
import org.jetbrains.kotlin.testGenerator.model.Patterns.KT_WITHOUT_DOT_AND_FIR_PREFIX
import org.jetbrains.kotlin.testGenerator.model.Patterns.KT_WITHOUT_FIR_PREFIX
import org.jetbrains.kotlin.testGenerator.model.Patterns.TEST
import org.jetbrains.uast.test.kotlin.comparison.*

fun main(@Suppress("UNUSED_PARAMETER") args: Array<String>) {
    generateK1Tests()
}

fun assembleK1Workspace(): TWorkspace = assembleWorkspace()

fun generateK1Tests(isUpToDateCheck: Boolean = false) {
    System.setProperty("java.awt.headless", "true")
    TestGenerator.write(assembleWorkspace(), isUpToDateCheck)
}

private fun assembleWorkspace(): TWorkspace = workspace(KotlinPluginMode.K1) {
    val excludedFirPrecondition = fun(name: String) = !name.endsWith(".fir.kt") && !name.endsWith(".fir.kts")

    testGroup("gradle/gradle-java/k1", category = GRADLE, testDataPath = "../../../idea/tests/testData") {
        testClass<AbstractGradleBuildFileHighlightingTest> {
            model(
                "gradle/highlighting/gradle8",
                pattern = DIRECTORY,
                isRecursive = false,
                setUpStatements = listOf("gradleVersion = \"8.6\";")
            )
            model(
                "gradle/highlighting/gradle7",
                pattern = DIRECTORY,
                isRecursive = false,
                setUpStatements = listOf("gradleVersion = \"7.6.4\";")
            )
        }
    }

    testGroup("jvm-debugger/test", category = DEBUGGER) {
        listOf(
          AbstractIrKotlinSteppingTest::class,
          AbstractIndyLambdaIrKotlinSteppingTest::class,
          AbstractK1IdeK2CodeKotlinSteppingTest::class,
        ).forEach {
            testClass(it) {
                model("stepping/stepIntoAndSmartStepInto", pattern = KT_WITHOUT_DOTS, testMethodName = "doStepIntoTest", testClassName = "StepInto")
                model("stepping/stepIntoAndSmartStepInto", pattern = KT_WITHOUT_DOTS, testMethodName = "doSmartStepIntoTest", testClassName = "SmartStepInto")
                model("stepping/stepInto", pattern = KT_WITHOUT_DOTS, testMethodName = "doStepIntoTest", testClassName = "StepIntoOnly")
                model("stepping/stepOut", pattern = KT_WITHOUT_DOTS, testMethodName = "doStepOutTest")
                model("stepping/stepOver", pattern = KT_WITHOUT_DOTS, testMethodName = "doStepOverTest")
                model("stepping/filters", pattern = KT_WITHOUT_DOTS, testMethodName = "doStepIntoTest")
                model("stepping/custom", pattern = KT_WITHOUT_DOTS, testMethodName = "doCustomTest")
            }
        }

        listOf(
            AbstractIndyLambdaIrKotlinEvaluateExpressionTest::class,
            AbstractIrKotlinEvaluateExpressionWithIRFragmentCompilerTest::class,
            AbstractK1IdeK2CodeKotlinEvaluateExpressionTest::class,
            AbstractInlineScopesAndK1IdeK2CodeEvaluateExpressionTest::class,
        ).forEach {
            testClass(it) {
                model("evaluation/singleBreakpoint", testMethodName = "doSingleBreakpointTest", targetBackend = TargetBackend.JVM_IR_WITH_IR_EVALUATOR)
                model("evaluation/multipleBreakpoints", testMethodName = "doMultipleBreakpointsTest", targetBackend = TargetBackend.JVM_IR_WITH_IR_EVALUATOR)
                model("evaluation/jvmMultiModule", testMethodName = "doJvmMultiModuleTest", targetBackend = TargetBackend.JVM_IR_WITH_IR_EVALUATOR)
            }
        }

        listOf(
          AbstractIrKotlinScriptEvaluateExpressionTest::class,
          AbstractK1IdeK2CodeScriptEvaluateExpressionTest::class,
        ).forEach {
            testClass(it) {
                model("evaluation/scripts", testMethodName = "doMultipleBreakpointsTest", targetBackend = TargetBackend.JVM_IR_WITH_IR_EVALUATOR)
            }
        }

        testClass<AbstractIrKotlinEvaluateExpressionInMppTest> {
            model("evaluation/singleBreakpoint", testMethodName = "doSingleBreakpointTest", targetBackend = TargetBackend.JVM_IR_WITH_IR_EVALUATOR)
            model("evaluation/multipleBreakpoints", testMethodName = "doMultipleBreakpointsTest", targetBackend = TargetBackend.JVM_IR_WITH_IR_EVALUATOR)
            model("evaluation/multiplatform", testMethodName = "doMultipleBreakpointsTest", targetBackend = TargetBackend.JVM_IR_WITH_IR_EVALUATOR)
        }

        testClass<AbstractK1IdeK2CodeKotlinEvaluateExpressionInMppTest> {
            model("evaluation/singleBreakpoint", testMethodName = "doSingleBreakpointTest", targetBackend = TargetBackend.JVM_IR_WITH_IR_EVALUATOR)
            model("evaluation/multipleBreakpoints", testMethodName = "doMultipleBreakpointsTest", targetBackend = TargetBackend.JVM_IR_WITH_IR_EVALUATOR)
            model("evaluation/multiplatform", testMethodName = "doMultipleBreakpointsTest", targetBackend = TargetBackend.JVM_IR_WITH_IR_EVALUATOR)
        }

        testClass<AbstractSelectExpressionForDebuggerTestWithAnalysisApi> {
            model("selectExpression")
        }

        testClass<AbstractSelectExpressionForDebuggerTestWithLegacyImplementation> {
            model("selectExpression")
        }

        testClass<AbstractPositionManagerTest> {
            model("positionManager", isRecursive = false, pattern = KT, testClassName = "SingleFile")
            model("positionManager", isRecursive = false, pattern = DIRECTORY, testClassName = "MultiFile")
        }


        listOf(AbstractIrBreakpointHighlightingTest::class,
               AbstractK1IdeK2CodeBreakpointHighlightingTest::class).forEach {
            testClass(it) {
                model("highlighting", isRecursive = false, pattern = KT_WITHOUT_DOTS, testMethodName = "doCustomTest")
            }
        }

        testClass<AbstractK1IdeK2CodeKotlinSteppingPacketsNumberTest> {
            model("stepping/packets", isRecursive = false, pattern = KT_WITHOUT_DOTS, testMethodName = "doCustomTest")
        }

        testClass<AbstractSmartStepIntoTest> {
            model("smartStepInto")
        }

        testClass<AbstractBreakpointApplicabilityTest> {
            model("breakpointApplicability", pattern = KT_OR_KTS)
        }

        listOf(AbstractFileRankingTest::class, AbstractK1IdeK2CodeFileRankingTest::class).forEach {
            testClass(it) {
                model("fileRanking")
            }
        }

        listOf(AbstractSuspendStackTraceTest::class, AbstractK1IdeK2CodeSuspendStackTraceTest::class).forEach {
            testClass(it) {
                model("suspendStackTrace")
            }
        }

        testClass<AbstractFlowAsyncStackTraceTest> {
            model("asyncStackTrace/flows")
        }

        listOf(AbstractCoroutineDumpTest::class, AbstractK1IdeK2CodeCoroutineDumpTest::class).forEach {
            testClass(it) {
                model("coroutines")
            }
        }

        testClass<AbstractIrSequenceTraceTestCase> { // TODO: implement mapping logic for terminal operations
            model("sequence/streams/sequence", excludedDirectories = listOf("terminal"))
        }

        listOf(AbstractContinuationStackTraceTest::class, AbstractK1IdeK2CodeContinuationStackTraceTest::class).forEach {
            testClass(it) {
                model("continuation")
            }
        }

        listOf(AbstractKotlinVariablePrintingTest::class, AbstractK1IdeK2CodeKotlinVariablePrintingTest::class).forEach {
            testClass(it) {
                model("variables", isRecursive = false)
            }
        }

        listOf(AbstractXCoroutinesStackTraceTest::class, AbstractK1IdeK2CodeXCoroutinesStackTraceTest::class).forEach {
            testClass(it) {
                model("xcoroutines")
            }
        }

        testClass<AbstractClassNameCalculatorTest> {
            model("classNameCalculator")
        }

        testClass<AbstractKotlinExceptionFilterTest> {
            model("exceptionFilter", pattern = Patterns.forRegex("""^([^.]+)$"""), isRecursive = false)
        }
    }

    testGroup("copyright/tests", category = CODE_INSIGHT) {
        testClass<AbstractUpdateKotlinCopyrightTest> {
            model("update", pattern = KT_OR_KTS, testMethodName = "doTest")
        }
    }

    testGroup("coverage/tests") {
        testClass<AbstractKotlinCoverageOutputFilesTest> {
            model("outputFiles")
        }
    }


    testGroup("idea/tests") {
        testClass<AbstractAdditionalResolveDescriptorRendererTest> {
            model("resolve/additionalLazyResolve")
        }

        testClass<AbstractPartialBodyResolveTest> {
            model("resolve/partialBodyResolve")
        }

        testClass<AbstractResolveModeComparisonTest> {
            model("resolve/resolveModeComparison")
        }
    }

    testGroup("idea/tests", category = HIGHLIGHTING) {
        testClass<AbstractKotlinHighlightVisitorTest>(commonSuite = false) {
            model("checker", isRecursive = false, pattern = KT.withPrecondition(excludedFirPrecondition))
            model("checker/regression", pattern = KT.withPrecondition(excludedFirPrecondition))
            model("checker/recovery", pattern = KT.withPrecondition(excludedFirPrecondition))
            model("checker/rendering", pattern = KT.withPrecondition(excludedFirPrecondition))
            model("checker/scripts", pattern = KTS.withPrecondition(excludedFirPrecondition))
            model("checker/duplicateJvmSignature", pattern = KT.withPrecondition(excludedFirPrecondition))
            model("checker/infos", testMethodName = "doTestWithInfos", pattern = KT.withPrecondition(excludedFirPrecondition))
            model("checker/diagnosticsMessage", pattern = KT.withPrecondition(excludedFirPrecondition))
        }

        testClass<AbstractKotlinHighlightWolfPassTest>(commonSuite = false) {
            model("checker/wolf", isRecursive = false, pattern = KT.withPrecondition(excludedFirPrecondition))
        }

        testClass<AbstractJavaAgainstKotlinSourceCheckerTest> {
            model("kotlinAndJavaChecker/javaAgainstKotlin")
            model("kotlinAndJavaChecker/javaWithKotlin")
        }

        testClass<AbstractJavaAgainstKotlinBinariesCheckerTest> {
            model("kotlinAndJavaChecker/javaAgainstKotlin")
        }

        testClass<AbstractHighlightingTest>(commonSuite = false) {
            model("highlighter", pattern = KT_OR_JAVA)
        }

        testClass<AbstractK1HighlightingMetaInfoTest> {
            model("highlighterMetaInfo", pattern = KT_OR_KTS)
        }

        testClass<AbstractDslHighlighterTest> {
            model("dslHighlighter")
        }

        testClass<AbstractUsageHighlightingTest> {
            model("usageHighlighter")
        }

        testClass<AbstractKotlinFoldingTest> {
            model("folding/noCollapse")
            model("folding/checkCollapse", testMethodName = "doSettingsFoldingTest")
        }

        testClass<AbstractKDocHighlightingTest> {
            model("kdoc/highlighting")
        }
    }
    testGroup("idea/tests", category = CODE_INSIGHT) {
        testClass<AbstractK1PsiUnifierTest> {
            model("unifier")
        }

        testClass<AbstractK1CodeFragmentHighlightingTest> {
            model("checker/codeFragments", pattern = KT, isRecursive = false)
            model("checker/codeFragments/imports", testMethodName = "doTestWithImport", pattern = KT)
        }

        testClass<AbstractK1CodeFragmentAutoImportTest> {
            model("quickfix.special/codeFragmentAutoImport", pattern = KT, isRecursive = false)
        }

        testClass<AbstractJsCheckerTest> {
            model("checker/js")
        }

        testClass<AbstractParameterInfoTest> {
            model(
                "parameterInfo",
                pattern = Patterns.forRegex("^([\\w\\-_]+)\\.kt$"),
                isRecursive = true,
                excludedDirectories = listOf("withLib1/sharedLib", "withLib2/sharedLib", "withLib3/sharedLib"),
            )
        }
    }

    testGroup("idea/tests", category = QUICKFIXES) {
        testClass<AbstractK1QuickFixTest> {
            model(
                "quickfix",
                pattern = Patterns.forRegex("^([\\w\\-_]+)\\.kts?$"),
                excludedDirectories = listOf(
                    "addAnnotationUseSiteTargetForConstructorParameter",
                    "simplifyExpression",
                    "redundantInterpolationPrefix", // K2-only multi-dollar interpolation
                    "addInterpolationPrefixUnresolvedReference", // K2-only multi-dollar interpolation
                )
            )
        }

        testClass<AbstractQuickFixMultiFileTest> {
            model("quickfix", pattern = Patterns.forRegex("""^(\w+)\.((before\.Main\.\w+)|(test))$"""), testMethodName = "doTestWithExtraFile")
        }

        testClass<AbstractQuickFixMultiModuleTest> {
            model("multiModuleQuickFix", pattern = DIRECTORY, depth = 1)
        }
    }

    testGroup("idea/tests", category = NAVIGATION) {
        testClass<AbstractGotoSuperTest> {
            model("navigation/gotoSuper", pattern = TEST, isRecursive = false)
        }

        testClass<AbstractGotoTypeDeclarationTest> {
            model("navigation/gotoTypeDeclaration", pattern = TEST)
        }

        testClass<AbstractGotoDeclarationTest> {
            model("navigation/gotoDeclaration", pattern = TEST)
        }

        testClass<AbstractKotlinGotoTest> {
            model("navigation/gotoClass", testMethodName = "doClassTest")
            model("navigation/gotoSymbol", testMethodName = "doSymbolTest")
        }

        testClass<AbstractNavigateToLibrarySourceTest> {
            model("decompiler/navigation/usercode")
        }

        testClass<AbstractNavigateJavaSourceToLibraryTest> {
            model("decompiler/navigation/userJavaCode", pattern = Patterns.forRegex("^(.+)\\.java$"))
        }

        testClass<AbstractNavigateJavaSourceToLibrarySourceTest> {
            model("navigation/javaSource", pattern = Patterns.forRegex("^(.+)\\.java$"))
        }

        testClass<AbstractNavigateToLibrarySourceTestWithJS> {
            model("decompiler/navigation/usercode", testClassName = "UsercodeWithJSModule")
        }

        testClass<AbstractNavigateToDecompiledLibraryTest> {
            model("decompiler/navigation/usercode")
        }

        testClass<AbstractKotlinGotoImplementationTest> {
            model("navigation/implementations", isRecursive = false)
        }

        testClass< AbstractKotlinGotoImplementationMultifileTest> {
            model("navigation/implementations/multifile/javaKotlin", testMethodName = "doJavaKotlinTest")
            model("navigation/implementations/multifile/kotlinJava", testMethodName = "doKotlinJavaTest")
        }

        testClass<AbstractGotoTestOrCodeActionTest> {
            model("navigation/gotoTestOrCode", pattern = Patterns.forRegex("^(.+)\\.main\\..+$"))
        }

        testClass<AbstractKotlinGotoImplementationMultiModuleTest> {
            model("navigation/implementations/multiModule", isRecursive = false, pattern = DIRECTORY)
        }

        testClass<AbstractKotlinGotoRelatedSymbolMultiModuleTest> {
            model("navigation/relatedSymbols/multiModule", isRecursive = false, pattern = DIRECTORY)
        }

        testClass<AbstractKotlinGotoSuperMultiModuleTest> {
            model("navigation/gotoSuper/multiModule", isRecursive = false, pattern = DIRECTORY)
        }
    }

    testGroup("idea/tests", category = FIND_USAGES) {
        testClass<AbstractInheritorsSearchTest> {
            model("search/inheritance")
        }

        testClass<AbstractAnnotatedMembersSearchTest> {
            model("search/annotations")
        }

        testClass<AbstractK1ExternalAnnotationTest> {
            model("externalAnnotations", pattern = KT_WITHOUT_DOTS)
        }

        testClass<AbstractKotlinTypeAliasByExpansionShortNameIndexTest> {
            model("typealiasExpansionIndex")
        }

    }

    testGroup("idea/tests", category = CODE_INSIGHT) {
        testClass<AbstractSurroundWithTest> {
            model("codeInsight/surroundWith/if", testMethodName = "doTestWithIfSurrounder")
            model("codeInsight/surroundWith/ifElse", testMethodName = "doTestWithIfElseSurrounder")
            model("codeInsight/surroundWith/ifElseExpression", testMethodName = "doTestWithIfElseExpressionSurrounder")
            model("codeInsight/surroundWith/ifElseExpressionBraces", testMethodName = "doTestWithIfElseExpressionBracesSurrounder")
            model("codeInsight/surroundWith/not", testMethodName = "doTestWithNotSurrounder")
            model("codeInsight/surroundWith/parentheses", testMethodName = "doTestWithParenthesesSurrounder")
            model("codeInsight/surroundWith/stringTemplate", testMethodName = "doTestWithStringTemplateSurrounder")
            model("codeInsight/surroundWith/when", testMethodName = "doTestWithWhenSurrounder")
            model("codeInsight/surroundWith/tryCatch", testMethodName = "doTestWithTryCatchSurrounder")
            model("codeInsight/surroundWith/tryCatchExpression", testMethodName = "doTestWithTryCatchExpressionSurrounder")
            model("codeInsight/surroundWith/tryCatchFinally", testMethodName = "doTestWithTryCatchFinallySurrounder")
            model("codeInsight/surroundWith/tryCatchFinallyExpression", testMethodName = "doTestWithTryCatchFinallyExpressionSurrounder")
            model("codeInsight/surroundWith/tryFinally", testMethodName = "doTestWithTryFinallySurrounder")
            model("codeInsight/surroundWith/tryFinallyExpression", testMethodName = "doTestWithTryFinallyExpressionSurrounder")
            model("codeInsight/surroundWith/functionLiteral", testMethodName = "doTestWithFunctionLiteralSurrounder")
            model("codeInsight/surroundWith/withIfExpression", testMethodName = "doTestWithSurroundWithIfExpression")
            model("codeInsight/surroundWith/withIfElseExpression", testMethodName = "doTestWithSurroundWithIfElseExpression")
        }

        testClass<AbstractJoinLinesTest> {
            model("joinLines")
        }

        testClass<AbstractBreadcrumbsTest> {
            model("codeInsight/breadcrumbs", pattern = KT_OR_KTS)
        }

        testClass<AbstractPairMatcherTest> {
            model("codeInsight/pairMatcher", pattern = KT_OR_KTS)
        }

        testClass<AbstractConcatenatedStringGeneratorTest> {
            model("concatenatedStringGenerator", pattern = Patterns.forRegex("^([\\w\\-_]+)\\.kt$"))
        }
    }

    testGroup("idea/tests", category = INTENTIONS) {
        testClass<AbstractK1IntentionTest> {
            model(
                "intentions", pattern = Patterns.forRegex("^([\\w\\-_]+)\\.(kt|kts)$"),
                excludedDirectories = listOf(
                    "convertToMultiDollarString", // K2-only
                    "branched/ifWhen/ifToWhen/whenGuards", // K2-only
                    "concatenationToBuildCollection", // K2-only
                    "convertStringTemplateToBuildStringMultiDollarPrefix", // K2-only
                    "convertToConcatenatedStringMultiDollarPrefix", // K2-only
                    "convertToStringTemplateInterpolationPrefix", // K2-only
                )
            )
        }

        testClass<AbstractK1IntentionTest2> {
            model("intentions/loopToCallChain", pattern = Patterns.forRegex("^([\\w\\-_]+)\\.kt$"))
        }
    }

    testGroup("idea/tests", category = INSPECTIONS) {
        testClass<AbstractInspectionTest> {
            model("intentions", pattern = Patterns.forRegex("^(inspections\\.test)$"), flatten = true)
            model("inspections", pattern = Patterns.forRegex("^(inspections\\.test)$"), flatten = true)
            model("inspectionsLocal", pattern = Patterns.forRegex("^(inspections\\.test)$"), flatten = true)
        }

        testClass<AbstractLocalInspectionTest> {
            model(
                "inspectionsLocal", pattern = Patterns.forRegex("^([\\w\\-_]+)\\.(kt|kts)$"),
                excludedDirectories = listOf(
                    "unusedVariable", // In FE1.0, this is a quickfix rather than a local inspection
                    "canSimplifyDollarLiteral", // K2-only
                    "canConvertToMultiDollarString", // K2-only
                    "branched/introduceWhenSubject/whenGuards", // K2-only
                    "removeRedundantLabel", // quick fix in K1
                    "contextParametersMigration", // K2-only
                    "defaultAnnotationTarget", // K2-only
                    "orInWhenGuard", // K2-only
                    "convertFromMultiDollarToRegularString", // K2-only
                    "javaCollectionsWithNullableTypes", // K2-only
                )
            )
        }

        testClass<AbstractViewOfflineInspectionTest> {
            model("inspectionsLocal", pattern = Patterns.forRegex("^([\\w\\-_]+)_report\\.(xml)$"))
        }
    }

    testGroup("idea/tests", category = CODE_INSIGHT) {
        testClass<AbstractHierarchyTest> {
            model("hierarchy/class/type", pattern = DIRECTORY, isRecursive = false, testMethodName = "doTypeClassHierarchyTest")
            model("hierarchy/class/super", pattern = DIRECTORY, isRecursive = false, testMethodName = "doSuperClassHierarchyTest")
            model("hierarchy/class/sub", pattern = DIRECTORY, isRecursive = false, testMethodName = "doSubClassHierarchyTest")
            model("hierarchy/calls/callers", pattern = DIRECTORY, isRecursive = false, testMethodName = "doCallerHierarchyTest")
            model("hierarchy/calls/callersJava", pattern = DIRECTORY, isRecursive = false, testMethodName = "doCallerJavaHierarchyTest")
            model("hierarchy/calls/callees", pattern = DIRECTORY, isRecursive = false, testMethodName = "doCalleeHierarchyTest")
            model("hierarchy/overrides", pattern = DIRECTORY, isRecursive = false, testMethodName = "doOverrideHierarchyTest")
        }

        testClass<AbstractHierarchyWithLibTest> {
            model("hierarchy/withLib", pattern = DIRECTORY, isRecursive = false)
        }

        testClass<AbstractMoveStatementTest> {
            model("codeInsight/moveUpDown/classBodyDeclarations", pattern = KT_OR_KTS, testMethodName = "doTestClassBodyDeclaration")
            model("codeInsight/moveUpDown/closingBraces", testMethodName = "doTestExpression")
            model("codeInsight/moveUpDown/expressions", pattern = KT_OR_KTS, testMethodName = "doTestExpression")
            model("codeInsight/moveUpDown/line", testMethodName = "doTestLine")
            model("codeInsight/moveUpDown/parametersAndArguments", testMethodName = "doTestExpression")
            model("codeInsight/moveUpDown/trailingComma", testMethodName = "doTestExpressionWithTrailingComma")
        }

        testClass<AbstractMoveLeftRightTest> {
            model("codeInsight/moveLeftRight")
        }


        testClass<AbstractUnwrapRemoveTest> {
            model("codeInsight/unwrapAndRemove/removeExpression", testMethodName = "doTestExpressionRemover")
            model("codeInsight/unwrapAndRemove/unwrapThen", testMethodName = "doTestThenUnwrapper")
            model("codeInsight/unwrapAndRemove/unwrapElse", testMethodName = "doTestElseUnwrapper")
            model("codeInsight/unwrapAndRemove/removeElse", testMethodName = "doTestElseRemover")
            model("codeInsight/unwrapAndRemove/unwrapLoop", testMethodName = "doTestLoopUnwrapper")
            model("codeInsight/unwrapAndRemove/unwrapTry", testMethodName = "doTestTryUnwrapper")
            model("codeInsight/unwrapAndRemove/unwrapCatch", testMethodName = "doTestCatchUnwrapper")
            model("codeInsight/unwrapAndRemove/removeCatch", testMethodName = "doTestCatchRemover")
            model("codeInsight/unwrapAndRemove/unwrapFinally", testMethodName = "doTestFinallyUnwrapper")
            model("codeInsight/unwrapAndRemove/removeFinally", testMethodName = "doTestFinallyRemover")
            model("codeInsight/unwrapAndRemove/unwrapLambda", testMethodName = "doTestLambdaUnwrapper")
            model("codeInsight/unwrapAndRemove/unwrapFunctionParameter", testMethodName = "doTestFunctionParameterUnwrapper")
        }

        testClass<AbstractK1ExpressionTypeTest> {
            model("codeInsight/expressionType", pattern = KT or TEST)
        }

        testClass<AbstractRenderingKDocTest> {
            model("codeInsight/renderingKDoc")
        }

        testClass<AbstractBackspaceHandlerTest> {
            model("editor/backspaceHandler")
        }

        testClass<AbstractQuickDocProviderTest> {
            model("editor/quickDoc", pattern = Patterns.forRegex("""^([^_]+)\.(kt|java)$"""), isRecursive = false)
        }
    }

    testGroup("idea/tests", category = INLINE_REFACTORING) {
        testClass<AbstractInlineTest> {
            model("refactoring/inline", pattern = KT_WITHOUT_DOTS, excludedDirectories = listOf("withFullJdk"))
        }

        testClass<AbstractInlineTestWithSomeDescriptors> {
            model("refactoring/inline/withFullJdk", pattern = KT_WITHOUT_DOTS)
        }

        testClass<AbstractInlineMultiFileTest> {
            model("refactoring/inlineMultiFile", pattern = TEST, flatten = true)
        }
    }

    testGroup("idea/tests", category = REFACTORING) {
        testClass<AbstractSafeDeleteTest> {
            model("refactoring/safeDelete/deleteClass/kotlinClass", testMethodName = "doClassTest")
            model("refactoring/safeDelete/deleteClass/kotlinClassWithJava", testMethodName = "doClassTestWithJava")
            model("refactoring/safeDelete/deleteClass/javaClassWithKotlin", pattern = JAVA, testMethodName = "doJavaClassTest")
            model("refactoring/safeDelete/deleteObject/kotlinObject", testMethodName = "doObjectTest")
            model("refactoring/safeDelete/deleteFunction/kotlinFunction", testMethodName = "doFunctionTest")
            model("refactoring/safeDelete/deleteFunction/kotlinFunctionWithJava", testMethodName = "doFunctionTestWithJava")
            model("refactoring/safeDelete/deleteFunction/javaFunctionWithKotlin", testMethodName = "doJavaMethodTest")
            model("refactoring/safeDelete/deleteProperty/kotlinProperty", testMethodName = "doPropertyTest")
            model("refactoring/safeDelete/deleteProperty/kotlinPropertyWithJava", testMethodName = "doPropertyTestWithJava")
            model("refactoring/safeDelete/deleteProperty/javaPropertyWithKotlin", testMethodName = "doJavaPropertyTest")
            model("refactoring/safeDelete/deleteTypeAlias/kotlinTypeAlias", testMethodName = "doTypeAliasTest")
            model("refactoring/safeDelete/deleteTypeParameter/kotlinTypeParameter", testMethodName = "doTypeParameterTest")
            model("refactoring/safeDelete/deleteTypeParameter/kotlinTypeParameterWithJava", testMethodName = "doTypeParameterTestWithJava")
            model("refactoring/safeDelete/deleteValueParameter/kotlinValueParameter", testMethodName = "doValueParameterTest")
            model(
                "refactoring/safeDelete/deleteValueParameter/kotlinValueParameterWithJava",
                testMethodName = "doValueParameterTestWithJava"
            )
            model(
                "refactoring/safeDelete/deleteValueParameter/javaParameterWithKotlin",
                pattern = JAVA,
                testMethodName = "doJavaParameterTest"
            )
        }

        testClass<AbstractNameSuggestionProviderTest> {
            model("refactoring/nameSuggestionProvider")
        }
    }

    testGroup("idea/tests") {
        testClass<AbstractReferenceResolveTest> {
            model("resolve/references", pattern = KT_WITHOUT_DOTS)
        }

        testClass<AbstractReferenceResolveInJavaTest> {
            model("resolve/referenceInJava/binaryAndSource", pattern = JAVA)
            model("resolve/referenceInJava/sourceOnly", pattern = JAVA)
        }

        testClass<AbstractReferenceToCompiledKotlinResolveInJavaTest> {
            model("resolve/referenceInJava/binaryAndSource", pattern = JAVA)
        }

        testClass<AbstractReferenceResolveWithLibTest> {
            model("resolve/referenceWithLib", pattern = DIRECTORY, isRecursive = false)
        }

        testClass<AbstractReferenceResolveWithCompiledLibTest> {
            model("resolve/referenceWithLib", pattern = DIRECTORY, isRecursive = false)
        }

        testClass<AbstractReferenceResolveWithCrossLibTest> {
            model("resolve/referenceWithLib", pattern = DIRECTORY, isRecursive = false)
        }

        testClass<AbstractReferenceResolveWithCompilerPluginsWithLibTest> {
            model("resolve/referenceWithCompilerPluginsWithLib", pattern = DIRECTORY, isRecursive = false)
        }

        testClass<AbstractReferenceResolveWithCompilerPluginsWithCompiledLibTest> {
            model("resolve/referenceWithCompilerPluginsWithLib", pattern = DIRECTORY, isRecursive = false)
        }

        testClass<AbstractReferenceResolveWithCompilerPluginsWithCrossLibTest> {
            model("resolve/referenceWithCompilerPluginsWithLib", pattern = DIRECTORY, isRecursive = false)
        }

        testClass<AbstractReferenceResolveInLibrarySourcesTest> {
            model("resolve/referenceInLib", isRecursive = false)
        }

        testClass<AbstractReferenceToJavaWithWrongFileStructureTest> {
            model("resolve/referenceToJavaWithWrongFileStructure", isRecursive = false)
        }
    }

    testGroup("idea/tests", category = FIND_USAGES) {
        testClass<AbstractFindUsagesTest> {
            model("findUsages/kotlin", pattern = Patterns.forRegex("""^(.+)\.0\.kt$"""))
            model("findUsages/java", pattern = Patterns.forRegex("""^(.+)\.0\.java$"""))
            model("findUsages/propertyFiles", pattern = Patterns.forRegex("""^(.+)\.0\.properties$"""))
        }

        testClass<AbstractFindUsagesMultiModuleTest> {
            model("multiModuleFindUsages", isRecursive = false, pattern = DIRECTORY)
        }

        testClass<AbstractKotlinScriptFindUsagesTest> {
            model("findUsages/kotlinScript", pattern = Patterns.forRegex("""^(.+)\.0\.kts$"""))
        }

        testClass<AbstractFindUsagesWithDisableComponentSearchTest> {
            model("findUsages/kotlin/conventions/components", pattern = Patterns.forRegex("""^(.+)\.0\.(kt|kts)$"""))
        }

        testClass<AbstractKotlinFindUsagesWithLibraryTest> {
            model("findUsages/libraryUsages", pattern = Patterns.forRegex("""^(.+)\.0\.(kt|java)$"""))
        }

        testClass<AbstractKotlinFindUsagesWithStdlibTest> {
            model("findUsages/stdlibUsages", pattern = Patterns.forRegex("""^(.+)\.0\.kt$"""))
        }

        testClass<AbstractKotlinGroupUsagesBySimilarityTest> {
            model("findUsages/similarity/grouping", pattern = KT)
        }

        testClass<AbstractKotlinGroupUsagesBySimilarityFeaturesTest> {
            model("findUsages/similarity/features", pattern = KT)
        }
    }

    testGroup("idea/tests", category = MOVE_REFACTORING) {
        testClass<AbstractMoveTest> {
            model("refactoring/changePackage", pattern = TEST, flatten = true)
            model("refactoring/movePackage", pattern = TEST, flatten = true)
            model("refactoring/moveDirectory", pattern = TEST, flatten = true)
            model("refactoring/moveFile", pattern = TEST, flatten = true)
            model("refactoring/moveTopLevel", pattern = TEST, flatten = true)
            model("refactoring/moveTopLevelToInner", pattern = TEST, flatten = true)
            model("refactoring/moveNested", pattern = TEST, flatten = true)
        }

        testClass<AbstractK1MultiModuleMoveTest> {
            model("refactoring/moveMultiModule", pattern = TEST, flatten = true)
        }
    }

    testGroup("idea/tests", category = REFACTORING) {
        testClass<AbstractCopyTest> {
            model("refactoring/copy", pattern = TEST, flatten = true)
        }

        testClass<AbstractMultiModuleCopyTest> {
            model("refactoring/copyMultiModule", pattern = TEST, flatten = true)
        }

        testClass<AbstractMultiModuleSafeDeleteTest> {
            model("refactoring/safeDeleteMultiModule", pattern = TEST, flatten = true)
        }
    }

    testGroup("idea/tests", category = INTENTIONS) {
        testClass<AbstractK1MultiFileIntentionTest> {
            model("multiFileIntentions", pattern = TEST, flatten = true)
        }

        testClass<AbstractMultiFileLocalInspectionTest> {
            model("multiFileLocalInspections", pattern = TEST, flatten = true)
        }
    }

    testGroup("idea/tests", category = INSPECTIONS) {
        testClass<AbstractMultiFileInspectionTest> {
            model("multiFileInspections", pattern = TEST, flatten = true)
        }
    }

    testGroup("idea/tests", category = CODE_INSIGHT) {
        testClass<AbstractFormatterTest> {
            model("formatter", pattern = Patterns.forRegex("""^([^.]+)\.after\.kt.*$"""))
            model(
                "formatter/trailingComma",
                pattern = Patterns.forRegex("""^([^.]+)\.call\.after\.kt.*$"""),
                testMethodName = "doTestCallSite",
                testClassName = "FormatterCallSite"
            )
            model(
                "formatter",
                pattern = Patterns.forRegex("""^([^.]+)\.after\.inv\.kt.*$"""),
                testMethodName = "doTestInverted",
                testClassName = "FormatterInverted"
            )
            model(
                "formatter/trailingComma",
                pattern = Patterns.forRegex("""^([^.]+)\.call\.after\.inv\.kt.*$"""),
                testMethodName = "doTestInvertedCallSite",
                testClassName = "FormatterInvertedCallSite",
            )
        }
    }

    testGroup("idea/tests") {
        testClass<AbstractDiagnosticMessageTest> {
            model("diagnosticMessage", isRecursive = false)
        }
    }

    testGroup("idea/tests", category = RENAME_REFACTORING) {
        testClass<AbstractRenameTest> {
            model("refactoring/rename", pattern = TEST, flatten = true)
        }

        testClass<AbstractInplaceRenameTest> {
            model("refactoring/rename/inplace", pattern = KT, flatten = true)
        }

        testClass<AbstractMultiModuleRenameTest> {
            model("refactoring/renameMultiModule", pattern = TEST, flatten = true)
        }
    }

    testGroup("idea/tests", category = CODE_INSIGHT) {
        testClass<AbstractKotlinProjectViewTest> {
            model("projectView", pattern = TEST)
        }

        testClass<AbstractOutOfBlockModificationTest> {
            model("codeInsight/outOfBlock", pattern = Patterns.forRegex("^(.+)\\.(kt|kts|java)$"))
        }

        testClass<AbstractChangeLocalityDetectorTest> {
            model("codeInsight/changeLocality", pattern = KT_OR_KTS)
        }

        testClass<AbstractDataFlowValueRenderingTest> {
            model("dataFlowValueRendering")
        }

        testClass<AbstractLiteralTextToKotlinCopyPasteTest> {
            model("copyPaste/plainTextLiteral", pattern = Patterns.forRegex("""^([^.]+)\.txt$"""))
        }

        testClass<AbstractLiteralKotlinToKotlinCopyPasteTest> {
            model("copyPaste/literal", pattern = Patterns.forRegex("""^([^.]+)\.kt$"""))
        }

        testClass<AbstractInsertImportOnPasteTest> {
            model("copyPaste/imports", pattern = KT_WITHOUT_DOTS, testMethodName = "doTestCopy", testClassName = "Copy", isRecursive = true)
            model("copyPaste/imports", pattern = KT_WITHOUT_DOTS, testMethodName = "doTestCut", testClassName = "Cut", isRecursive = true)
        }

        testClass<AbstractMoveOnCutPasteTest> {
            model("copyPaste/moveDeclarations", pattern = KT_WITHOUT_DOTS, testMethodName = "doTest")
        }
    }

    testGroup("idea/tests", category = HIGHLIGHTING) {
        testClass<AbstractCustomHighlightUsageHandlerTest>("org.jetbrains.kotlin.idea.highlighter.HighlightExitPointsTestGenerated") {
            model("exitPoints")
        }

        testClass<AbstractCustomHighlightUsageHandlerTest>("org.jetbrains.kotlin.idea.highlighter.KotlinHighlightUsagesTestGenerated") {
            model("highlightUsages")
        }

        testClass<AbstractKotlinReceiverUsageHighlightingTest>("org.jetbrains.kotlin.idea.highlighter.KotlinReceiverUsageHighlightingTestGenerated") {
            model("receiverUsageHighlighting")
        }

        testClass<AbstractLineMarkersTest> {
            model("codeInsight/lineMarker", pattern = Patterns.forRegex("^(\\w+)\\.(kt|kts)$"))
        }

        testClass<AbstractKotlinPsiBasedTestFrameworkTest> {
            model(
                "codeInsight/lineMarker/runMarkers",
                pattern = Patterns.forRegex("^((jUnit|test)\\w*)\\.kt$"),
                testMethodName = "doPsiBasedTest",
                testClassName = "WithLightTestFramework"
            )
            model(
                "codeInsight/lineMarker/runMarkers",
                pattern = Patterns.forRegex("^((jUnit|test)\\w*)\\.kt$"),
                testMethodName = "doPureTest",
                testClassName = "WithoutLightTestFramework"
            )
            model(
                "codeInsight/lineMarker/runMarkers",
                pattern = Patterns.forRegex("^((jUnit|test)\\w*)\\.kt$"),
                testMethodName = "doTestWithGradleConfiguration",
                testClassName = "WithGradleConfiguration"
            )
        }

        testClass<AbstractLineMarkersTestInLibrarySources> {
            model("codeInsightInLibrary/lineMarker", testMethodName = "doTestWithLibrary")
        }

        testClass<AbstractMultiModuleLineMarkerTest> {
            model("multiModuleLineMarker", pattern = DIRECTORY, isRecursive = false)
        }

        testClass<AbstractMultiFileHighlightingTest> {
            model("multiFileHighlighting", isRecursive = false)
        }

        testClass<AbstractMultiPlatformHighlightingTest> {
            model("multiModuleHighlighting/multiplatform/", isRecursive = false, pattern = DIRECTORY)
        }
    }

    testGroup("idea/tests", category = CODE_INSIGHT) {
        testClass<AbstractShortenRefsTest> {
            model("shortenRefs", pattern = KT_WITHOUT_DOTS)
        }

        testClass<AbstractAddImportTest> {
            model("addImport", pattern = KT_WITHOUT_DOTS)
        }

        testClass<AbstractAddImportAliasTest53> {
            model("addImportAlias", pattern = KT_WITHOUT_DOTS)
        }

        testClass<AbstractK1AddImportActionTest> {
            model("idea/actions/kotlinAddImportAction", pattern = KT_WITHOUT_DOTS)
        }

        testClass<AbstractSmartSelectionTest> {
            model("smartSelection", testMethodName = "doTestSmartSelection", pattern = KT_WITHOUT_DOTS)
        }

        testClass<AbstractWorkSelectionTest> {
            model("wordSelection", pattern = DIRECTORY)
        }

        testClass<AbstractKotlinFileStructureTest> {
            model("structureView/fileStructure", pattern = KT_OR_KTS_WITHOUT_DOTS)
        }

        testClass<AbstractExpressionSelectionTest> {
            model("expressionSelection", testMethodName = "doTestExpressionSelection", pattern = KT_WITHOUT_DOTS)
        }

        testClass<AbstractK1AutoImportTest> {
            model("editor/autoImport", testMethodName = "doTest", testClassName = "WithAutoImport", pattern = DIRECTORY, isRecursive = false)
        }

        testClass<AbstractK1FilteringAutoImportTest> {
            model("editor/autoImportExtension", testMethodName = "doTest", testClassName = "WithAutoImport", pattern = DIRECTORY, isRecursive = false)
        }

        testClass<AbstractJvmOptimizeImportsTest> {
            model("editor/optimizeImports/jvm", pattern = KT_OR_KTS_WITHOUT_DOTS)
            model("editor/optimizeImports/common", pattern = KT_WITHOUT_DOTS)
        }

        testClass<AbstractJsOptimizeImportsTest> {
            model("editor/optimizeImports/js", pattern = KT_WITHOUT_DOTS)
            model("editor/optimizeImports/common", pattern = KT_WITHOUT_DOTS)
        }

        testClass<AbstractEnterHandlerTest> {
            model("editor/enterHandler", pattern = Patterns.forRegex("""^([^.]+)\.after\.kt.*$"""), testMethodName = "doNewlineTest", testClassName = "DirectSettings")
            model("editor/enterHandler", pattern = Patterns.forRegex("""^([^.]+)\.after\.inv\.kt.*$"""), testMethodName = "doNewlineTestWithInvert", testClassName = "InvertedSettings")
        }

        testClass<AbstractKotlinCommenterTest> {
            model("editor/commenter", pattern = KT_WITHOUT_DOTS)
        }


        testClass<AbstractKDocTypingTest> {
            model("kdoc/typing")
        }

        testClass<AbstractGenerateTestSupportMethodActionTest> {
            model("codeInsight/generate/testFrameworkSupport")
        }

        testClass<AbstractGenerateHashCodeAndEqualsActionTest> {
            model("codeInsight/generate/equalsWithHashCode")
        }

        testClass<AbstractCodeInsightActionTest> {
            model("codeInsight/generate/secondaryConstructors")
        }

        testClass<AbstractGenerateToStringActionTest> {
            model("codeInsight/generate/toString")
        }

        val inlayHintsFileRegexp = Patterns.forRegex("^([^_]\\w+)\\.kt$")

        testClass<AbstractKotlinArgumentsHintsProviderTest> {
            model("codeInsight/hints/arguments", pattern = inlayHintsFileRegexp)
        }

        testClass<AbstractKotlinReferenceTypeHintsProviderTest> {
            model("codeInsight/hints/types", pattern = inlayHintsFileRegexp)
        }

        testClass<AbstractKotlinLambdasHintsProvider> {
            model("codeInsight/hints/lambda")
        }
        testClass<AbstractKotlinRangesHintsProviderTest> {
            model("codeInsight/hints/ranges")
        }

        testClass<AbstractKotlinCallChainHintsProviderTest> {
            model("codeInsight/hints/chainCall", pattern = inlayHintsFileRegexp)
        }

        testClass<AbstractKotlinNavBarTest> {
            model("navigationToolbar", pattern = KT_OR_KTS, isRecursive = false)
        }
    }

    testGroup("idea/tests", category = UNCATEGORIZED) {
        testClass<AbstractCommonDecompiledTextTest> {
            model("decompiler/decompiledText", pattern = Patterns.forRegex("""^([^\.]+)$"""))
        }

        testClass<AbstractJvmDecompiledTextTest> {
            model("decompiler/decompiledTextJvm", pattern = Patterns.forRegex("""^([^\.]+)$"""))
        }

        testClass<AbstractStubBuilderTest> {
            model("stubs", pattern = KT_OR_KTS)
        }

        testClass<AbstractMultiplatformAnalysisTest> {
            model("multiplatform", isRecursive = false, pattern = DIRECTORY)
        }

        testClass<AbstractBytecodeToolWindowTest> {
            model("internal/toolWindow", isRecursive = false, pattern = DIRECTORY)
        }

        testClass<AbstractBytecodeToolWindowMultiplatformTest> {
            model("internal/toolWindowMultiplatform", isRecursive = false, pattern = DIRECTORY, testMethodName = "doTestCommon", testClassName = "Common")
            model("internal/toolWindowMultiplatform", isRecursive = false, pattern = DIRECTORY, testMethodName = "doTestJvm", testClassName = "Jvm")
        }

        testClass<AbstractReferenceResolveTest>("org.jetbrains.kotlin.idea.kdoc.KdocResolveTestGenerated") {
            model("kdoc/resolve")
        }

        testClass<AbstractSlicerTreeTest> {
            model("slicer", excludedDirectories = listOf("mpp"))
        }

        testClass<AbstractSlicerLeafGroupingTest> {
            model("slicer/inflow", flatten = true)
        }

        testClass<AbstractSlicerNullnessGroupingTest> {
            model("slicer/inflow", flatten = true)
        }

        testClass<AbstractSlicerMultiplatformTest> {
            model("slicer/mpp", isRecursive = false, pattern = DIRECTORY)
        }
    }

    testGroup("idea/tests", category = REFACTORING) {
        testClass<AbstractPullUpTest> {
            model("refactoring/pullUp/k2k", pattern = KT, flatten = true, testClassName = "K2K", testMethodName = "doKotlinTest")
            model("refactoring/pullUp/k2j", pattern = KT, flatten = true, testClassName = "K2J", testMethodName = "doKotlinTest")
            model("refactoring/pullUp/j2k", pattern = JAVA, flatten = true, testClassName = "J2K", testMethodName = "doJavaTest")
        }

        testClass<AbstractPushDownTest> {
            model("refactoring/pushDown/k2k", pattern = KT, flatten = true, testClassName = "K2K", testMethodName = "doKotlinTest")
            model("refactoring/pushDown/k2j", pattern = KT, flatten = true, testClassName = "K2J", testMethodName = "doKotlinTest")
            model("refactoring/pushDown/j2k", pattern = JAVA, flatten = true, testClassName = "J2K", testMethodName = "doJavaTest")
        }
    }

    testGroup("idea/tests", category = EXTRACT_REFACTORING) {
        testClass<AbstractExtractionTest> {
            model("refactoring/introduceVariable", pattern = KT_OR_KTS_WITHOUT_DOTS, testMethodName = "doIntroduceVariableTest")
            model("refactoring/extractFunction", pattern = KT_OR_KTS, testMethodName = "doExtractFunctionTest")
            model("refactoring/introduceProperty", pattern = KT_OR_KTS, testMethodName = "doIntroducePropertyTest")
            model("refactoring/introduceParameter", pattern = KT_OR_KTS_WITHOUT_DOTS, testMethodName = "doIntroduceSimpleParameterTest")
            model("refactoring/introduceLambdaParameter", pattern = KT_OR_KTS, testMethodName = "doIntroduceLambdaParameterTest")
            model("refactoring/introduceJavaParameter", pattern = JAVA, testMethodName = "doIntroduceJavaParameterTest")
            model("refactoring/introduceTypeParameter", pattern = KT_OR_KTS, testMethodName = "doIntroduceTypeParameterTest")
            model("refactoring/introduceTypeAlias", pattern = KT_OR_KTS, testMethodName = "doIntroduceTypeAliasTest")
            model("refactoring/introduceConstant", pattern = KT_OR_KTS, testMethodName = "doIntroduceConstantTest")
            model("refactoring/extractSuperclass", pattern = KT_OR_KTS_WITHOUT_DOTS, testMethodName = "doExtractSuperclassTest")
            model("refactoring/extractInterface", pattern = KT_OR_KTS_WITHOUT_DOTS, testMethodName = "doExtractInterfaceTest")
        }
        testClass<AbstractInplaceIntroduceFunctionTest> {
            model("refactoring/extractFunctionInplace")
        }
    }

    testGroup("scripting-support", category = SCRIPTS) {
        // See KTIJ-31408 for the reasoning why these tests are not generated
        //testClass<AbstractScratchRunActionTest> {
        //    model("scratch", pattern = KTS, testMethodName = "doScratchCompilingTest", testClassName = "ScratchCompiling", isRecursive = false)
        //    model("scratch", pattern = KTS, testMethodName = "doScratchReplTest", testClassName = "ScratchRepl", isRecursive = false)
        //    model("scratch/multiFile", pattern = DIRECTORY, testMethodName = "doScratchMultiFileTest", testClassName = "ScratchMultiFile", isRecursive = false)
        //    model("worksheet", pattern = WS_KTS, testMethodName = "doWorksheetCompilingTest", testClassName = "WorksheetCompiling", isRecursive = false)
        //    model("worksheet", pattern = WS_KTS, testMethodName = "doWorksheetReplTest", testClassName = "WorksheetRepl", isRecursive = false)
        //    model("worksheet/multiFile", pattern = DIRECTORY, testMethodName = "doWorksheetMultiFileTest", testClassName = "WorksheetMultiFile", isRecursive = false)
        //    model("scratch/rightPanelOutput", pattern = KTS, testMethodName = "doRightPreviewPanelOutputTest", testClassName = "ScratchRightPanelOutput", isRecursive = false)
        //}

        testClass<AbstractScratchLineMarkersTest> {
            model("scratch/lineMarker", testMethodName = "doScratchTest", pattern = KT_OR_KTS)
        }

        testClass<AbstractScriptTemplatesFromDependenciesTest> {
            model("script/templatesFromDependencies", pattern = DIRECTORY, isRecursive = false)
        }
    }

    testGroup("idea/tests", category = SCRIPTS) {
        testClass<AbstractIdeReplCompletionTest> {
            model("repl/completion")
        }

        testClass<AbstractScriptConfigurationHighlightingTest> {
            model("script/definition/highlighting", pattern = DIRECTORY, isRecursive = false)
            model("script/definition/complex", pattern = DIRECTORY, isRecursive = false, testMethodName = "doComplexTest")
        }

        testClass<AbstractScriptConfigurationNavigationTest> {
            model("script/definition/navigation", pattern = DIRECTORY, isRecursive = false)
        }

        testClass<AbstractScriptConfigurationCompletionTest> {
            model("script/definition/completion", pattern = DIRECTORY, isRecursive = false)
        }

        testClass<AbstractScriptConfigurationInsertImportOnPasteTest> {
            model("script/definition/imports", testMethodName = "doTestCopy", testClassName = "Copy", pattern = DIRECTORY, isRecursive = false)
            model("script/definition/imports", testMethodName = "doTestCut", testClassName = "Cut", pattern = DIRECTORY, isRecursive = false)
        }

        testClass<AbstractScriptDefinitionsOrderTest> {
            model("script/definition/order", pattern = DIRECTORY, isRecursive = false)
        }
    }

    testGroup("maven/tests") {
        testClass<AbstractMavenConfigureProjectByChangingFileTest> {
            model("configurator/jvm", pattern = DIRECTORY, isRecursive = false, testMethodName = "doTestWithMaven")
        }

        testClass<AbstractKotlinMavenInspectionTest> {
            val mavenInspections = "maven-inspections"
            val pattern = Patterns.forRegex("^([\\w\\-]+).xml$")
            testDataRoot.resolve(mavenInspections).listFiles()!!.onEach { check(it.isDirectory) }.sorted().forEach {
                model("$mavenInspections/${it.name}", pattern = pattern, flatten = true)
            }
        }
    }

    testGroup("gradle/gradle-java/k1", testDataPath = "../../../idea/tests/testData", category = GRADLE) {
        testClass<AbstractGradleConfigureProjectByChangingFileTest> {
            model("configuration/gradle", pattern = DIRECTORY, isRecursive = false, testMethodName = "doTestGradle")
            model("configuration/gsk", pattern = DIRECTORY, isRecursive = false, testMethodName = "doTestGradle")
        }
    }

    testGroup("idea/tests", testDataPath = TestKotlinArtifacts.compilerTestData("compiler/testData")) {
        testClass<AbstractResolveByStubTest> {
            model(
                "loadJava/compiledKotlin",
                excludedDirectories = listOf(
                    "contextParameters", // K1 failure for K2 feature, see KTIJ-33144
                ),
            )
        }

        testClass<AbstractLoadJavaClsStubTest> {
            model(
                "loadJava/compiledKotlin",
                testMethodName = "doTestCompiledKotlin",
                excludedDirectories = listOf(
                    "contextParameters", // K1 failure for K2 feature, see KTIJ-33144
                ),
            )
        }

        testClass<AbstractIdeLightClassesByFqNameTest> {
            model(
                "asJava/lightClasses/lightClassByFqName",
                excludedDirectories = listOf(
                    "withTestCompilerPluginEnabled", // relevant only for K2
                ),
                pattern = KT_OR_KTS_WITHOUT_DOTS,
            )
        }

        testClass<AbstractIdeLightClassesByPsiTest> {
            model("asJava/lightClasses/lightClassByPsi", pattern = KT_OR_KTS_WITHOUT_DOTS)
        }

        testClass<AbstractIdeCompiledLightClassesByFqNameTest> {
            model(
                "asJava/lightClasses/lightClassByFqName",
                excludedDirectories = listOf(
                    "local",
                    "compilationErrors",
                    "ideRegression",
                    "script",
                    "withTestCompilerPluginEnabled", // relevant only for K2
                ),
                pattern = KT_OR_KTS_WITHOUT_DOTS,
            )
        }
    }

    // TODO: KTIJ-33510
    //testGroup("compiler-plugins/parcelize/tests/k1", testDataPath = "../testData") {
    //    testClass<AbstractParcelizeK1QuickFixTest> {
    //        model("quickfix", pattern = Patterns.forRegex("^([\\w\\-_]+)\\.kt$"))
    //    }
    //
    //    testClass<AbstractParcelizeK1CheckerTest> {
    //        model("checker", pattern = KT)
    //    }
    //}

    testGroup("completion/tests-k1", testDataPath = "../testData", category = COMPLETION) {
        testClass<AbstractCompiledKotlinInJavaCompletionTest> {
            model("injava", pattern = JAVA, isRecursive = false)
        }

        testClass<AbstractKotlinSourceInJavaCompletionTest> {
            model("injava", pattern = JAVA, isRecursive = false)
        }

        testClass<AbstractKotlinStdLibInJavaCompletionTest> {
            model("injava/stdlib", pattern = JAVA, isRecursive = false)
        }

        testClass<AbstractBasicCompletionWeigherTest> {
            model("weighers/basic", pattern = KT_OR_KTS_WITHOUT_DOTS)
        }

        testClass<AbstractSmartCompletionWeigherTest> {
            model("weighers/smart", pattern = KT_OR_KTS_WITHOUT_DOTS)
        }

        testClass<AbstractK1JSBasicCompletionTest> {
            model("basic/common", pattern = KT_WITHOUT_FIR_PREFIX)
            model("basic/js", pattern = KT_WITHOUT_FIR_PREFIX)
        }

        testClass<AbstractK1JvmBasicCompletionTest> {
            model("basic/common", pattern = KT_WITHOUT_FIR_PREFIX)
            model("basic/java", pattern = KT_WITHOUT_FIR_PREFIX)
        }

        testClass<AbstractJvmSmartCompletionTest> {
            model("smart")
        }

        testClass<AbstractDumbCompletionTest> {
            model("dumb")
        }

        testClass<AbstractKeywordCompletionTest> {
            model("keywords", isRecursive = false, pattern = KT.withPrecondition(excludedFirPrecondition))
        }

        testClass<AbstractJvmWithLibBasicCompletionTest> {
            model("basic/withLib", isRecursive = false, pattern = KT_WITHOUT_FIR_PREFIX)
        }

        testClass<AbstractBasicCompletionHandlerTest> {
            model("handlers/basic", pattern = KT_WITHOUT_DOT_AND_FIR_PREFIX)
        }

        testClass<AbstractSmartCompletionHandlerTest> {
            model("handlers/smart", pattern = KT_WITHOUT_FIR_PREFIX)
        }

        testClass<AbstractKeywordCompletionHandlerTest> {
            model("handlers/keywords", pattern = KT_WITHOUT_FIR_PREFIX)
        }

        testClass<AbstractJavaCompletionHandlerTest> {
            model("handlers/injava", pattern = JAVA)
        }

        testClass<AbstractCompletionCharFilterTest> {
            model("handlers/charFilter", pattern = KT_WITHOUT_DOT_AND_FIR_PREFIX)
        }

        testClass<AbstractMultiFileJvmBasicCompletionTest> {
            model("basic/multifile", pattern = DIRECTORY, isRecursive = false)
        }

        testClass<AbstractMultiFileSmartCompletionTest> {
            model("smartMultiFile", pattern = DIRECTORY, isRecursive = false)
        }

        testClass<AbstractK1JvmBasicCompletionTest>("org.jetbrains.kotlin.idea.completion.test.K1KDocCompletionTestGenerated") {
            model("kdoc", pattern = KT_WITHOUT_DOT_AND_FIR_PREFIX)
        }

        testClass<AbstractK1MLPerformanceCompletionTest>(commonSuite = false) {
            model("basic/common", pattern = KT_WITHOUT_FIR_PREFIX)
            model("basic/java", pattern = KT_WITHOUT_FIR_PREFIX)
        }

        testClass<AbstractJava8BasicCompletionTest> {
            model("basic/java8")
        }

        testClass<AbstractK1CompletionIncrementalResolveTest> {
            model("incrementalResolve")
        }

        testClass<AbstractMultiPlatformCompletionTest> {
            model("multiPlatform/actualDeclaration", isRecursive = false, pattern = DIRECTORY)
            model("multiPlatform/classDeclaration", isRecursive = false, pattern = DIRECTORY)
            model("multiPlatform/functionDeclaration", isRecursive = false, pattern = DIRECTORY)
        }
    }

    testGroup("idea/tests", testDataPath = "../../completion/testData", category = COMPLETION) {
        testClass<AbstractK1CodeFragmentCompletionHandlerTest> {
            model("handlers/runtimeCast")
        }

        testClass<AbstractK1CodeFragmentCompletionTest> {
            model("basic/codeFragments", pattern = KT)
        }
    }

    testGroup("j2k/k1.new/tests", testDataPath = "../../shared/tests/testData", category = J2K) {
        testClass<AbstractK1JavaToKotlinConverterSingleFileTest> {
            model("newJ2k", pattern = Patterns.forRegex("""^([^.]+)\.java$"""))
        }

        testClass<AbstractK1JavaToKotlinConverterSingleFileFullJDKTest> {
            model("fullJDK", pattern = Patterns.forRegex("""^([^.]+)\.java$"""))
        }

        testClass<AbstractK1JavaToKotlinConverterPartialTest> {
            model("partialConverter", pattern = Patterns.forRegex("""^([^.]+)\.java$"""))
        }

        testClass<AbstractK1JavaToKotlinCopyPasteConversionTest>(commonSuite = false) {
            model("copyPaste", pattern = Patterns.forRegex("""^([^.]+)\.java$"""))
        }

        testClass<AbstractK1TextJavaToKotlinCopyPasteConversionTest>(commonSuite = false) {
            model("copyPastePlainText", pattern = Patterns.forRegex("""^([^.]+)\.txt$"""))
        }

        testClass<AbstractK1JavaToKotlinConverterMultiFileTest>(commonSuite = false) {
            model("multiFile", pattern = DIRECTORY, isRecursive = false)
        }
    }

    testGroup("j2k/k1.new/tests", category = J2K) {
        testClass<AbstractCommonConstraintCollectorTest>(commonSuite = false) {
            model("inference/common")
        }

        testClass<AbstractNullabilityInferenceTest>(commonSuite = false) {
            model("inference/nullability")
        }

        testClass<AbstractMutabilityInferenceTest>(commonSuite = false) {
            model("inference/mutability")
        }
    }

    testGroup("compiler-reference-index/tests") {
        testClass<AbstractKotlinCompilerReferenceTest> {
            model("compilerIndex", pattern = DIRECTORY, classPerTest = true)
        }

        testClass<AbstractKotlinCompilerReferenceByReferenceTest> {
            model("compilerIndexByReference", pattern = DIRECTORY, classPerTest = true)
        }
    }

    testGroup("compiler-reference-index/tests", testDataPath = "../../idea/tests/testData") {
        testClass<AbstractFindUsagesWithCompilerReferenceIndexTest> {
            model("findUsages/kotlin", pattern = Patterns.forRegex("""^(.+)\.0\.kt$"""), classPerTest = true)
            model("findUsages/java", pattern = Patterns.forRegex("""^(.+)\.0\.java$"""), classPerTest = true)
            model("findUsages/propertyFiles", pattern = Patterns.forRegex("""^(.+)\.0\.properties$"""), classPerTest = true)
        }
    }

    testGroup("compiler-plugins/kotlinx-serialization/tests") {
        testClass<AbstractSerializationPluginIdeDiagnosticTest> {
            model("diagnostics")
        }
        testClass<AbstractSerializationQuickFixTest> {
            model("quickfix", pattern = Patterns.forRegex("^([\\w\\-_]+)\\.kt$"))
        }
    }

    testGroup("uast/uast-kotlin/tests", testDataPath = "../../uast-kotlin-fir/tests/testData") {
        testClass<AbstractFE1UastDeclarationTest> {
            model("declaration", pattern = KT_OR_KTS)
        }

        testClass<AbstractFE1UastTypesTest> {
            model("type")
        }

        testClass<AbstractFE1UastValuesTest> {
            model("value")
        }
    }

    testGroup("uast/uast-kotlin/tests") {
        testClass<AbstractFE1LegacyUastDeclarationTest> {
            model("")
        }

        testClass<AbstractFE1LegacyUastIdentifiersTest> {
            model("")
        }

        testClass<AbstractFE1LegacyUastResolveEverythingTest> {
            model("")
        }

        testClass<AbstractFE1LegacyUastTypesTest> {
            model("")
        }

        testClass<AbstractFE1LegacyUastValuesTest> {
            model("")
        }
    }

    testGroup("performance-tests", testDataPath = "../idea/tests/testData") {
        testClass<AbstractPerformanceJavaToKotlinCopyPasteConversionTest>(commonSuite = false) {
            model("copyPaste/conversion", testMethodName = "doPerfTest", pattern = Patterns.forRegex("""^([^.]+)\.java$"""))
        }

        testClass<AbstractPerformanceNewJavaToKotlinCopyPasteConversionTest>(commonSuite = false) {
            model("copyPaste/conversion", testMethodName = "doPerfTest", pattern = Patterns.forRegex("""^([^.]+)\.java$"""))
        }

        testClass<AbstractPerformanceLiteralKotlinToKotlinCopyPasteTest>(commonSuite = false) {
            model("copyPaste/literal", testMethodName = "doPerfTest", pattern = Patterns.forRegex("""^([^.]+)\.kt$"""))
        }

        testClass<AbstractPerformanceHighlightingTest>(commonSuite = false) {
            model("highlighter", testMethodName = "doPerfTest")
        }

        testClass<AbstractPerformanceHighlightingStatNamesTest>(commonSuite = false) {
            model("highlighter", testMethodName = "doPerfTest", pattern = Patterns.forRegex("""^(InvokeCall)\.kt$"""))
        }

        testClass<AbstractPerformanceAddImportTest>(commonSuite = false) {
            model("addImport", testMethodName = "doPerfTest", pattern = KT_WITHOUT_DOTS)
        }

        testClass<AbstractPerformanceTypingIndentationTest>(commonSuite = false) {
            model("editor/enterHandler", pattern = Patterns.forRegex("""^([^.]+)\.after\.kt.*$"""), testMethodName = "doNewlineTest", testClassName = "DirectSettings")
            model("editor/enterHandler", pattern = Patterns.forRegex("""^([^.]+)\.after\.inv\.kt.*$"""), testMethodName = "doNewlineTestWithInvert", testClassName = "InvertedSettings")
        }
    }

    testGroup("performance-tests", testDataPath = "../completion/testData") {
        testClass<AbstractPerformanceCompletionIncrementalResolveTest>(commonSuite = false) {
            model("incrementalResolve", testMethodName = "doPerfTest")
        }

        testClass<AbstractPerformanceBasicCompletionHandlerTest>(commonSuite = false) {
            model("handlers/basic", testMethodName = "doPerfTest", pattern = KT_WITHOUT_DOTS)
        }

        testClass<AbstractPerformanceBasicCompletionHandlerStatNamesTest>(commonSuite = false) {
            model("handlers/basic", testMethodName = "doPerfTest", pattern = Patterns.forRegex("""^(GetOperator)\.kt$"""))
        }

        testClass<AbstractPerformanceSmartCompletionHandlerTest>(commonSuite = false) {
            model("handlers/smart", testMethodName = "doPerfTest")
        }

        testClass<AbstractPerformanceKeywordCompletionHandlerTest>(commonSuite = false) {
            model("handlers/keywords", testMethodName = "doPerfTest")
        }

        testClass<AbstractPerformanceCompletionCharFilterTest>(commonSuite = false) {
            model("handlers/charFilter", testMethodName = "doPerfTest", pattern = KT_WITHOUT_DOTS)
        }
    }

    testGroup("idea/tests",  testDataPath = "../../code-insight/postfix-templates/testData", category = COMPLETION) {
        testClass<AbstractK1PostfixTemplateTest>(indexingMode = listOf(IndexingMode.DUMB_EMPTY_INDEX, IndexingMode.SMART)) {
            model("expansion/oldTestData", pattern = KT_WITHOUT_DOTS, passTestDataPath = false)
        }
    }

    testGroup("idea/tests",  testDataPath = "../../code-insight/testData", category = HIGHLIGHTING) {
        testClass<AbstractMultiModuleLineMarkerCodeMetaInfoTest> {
            model("linemarkers", isRecursive = false, pattern = DIRECTORY)
        }
    }

    testGroup("code-insight/intentions-shared/tests/k1", testDataPath = "../testData", category = INTENTIONS) {
        testClass<AbstractSharedK1IntentionTest> {
            model("intentions", pattern = Patterns.forRegex("^([\\w\\-_]+)\\.(kt|kts)$"))
        }
    }

    testGroup("code-insight/inspections-shared/tests/k1", testDataPath = "../testData", category = INSPECTIONS) {
        testClass<AbstractSharedK1LocalInspectionTest> {
            val pattern = Patterns.forRegex("^([\\w\\-_]+)\\.(kt|kts)$")
            model("inspectionsLocal", pattern = pattern)
        }

        testClass<AbstractSharedK1InspectionTest> {
            val pattern = Patterns.forRegex("^(inspections\\.test)$")
            model("inspections", pattern = pattern)
            model("inspectionsLocal", pattern = pattern)
        }

        testClass<AbstractSharedK1KDocHighlightingTest> {
            val pattern = Patterns.forRegex("^([\\w\\-_]+)\\.(kt|kts)$")
            model("kdoc/highlighting", pattern = pattern)
        }

        testClass<AbstractSharedK1QuickFixTest> {
            model("quickfix", pattern = Patterns.forRegex("^([\\w\\-_]+)\\.kt$"))
        }
    }
}
