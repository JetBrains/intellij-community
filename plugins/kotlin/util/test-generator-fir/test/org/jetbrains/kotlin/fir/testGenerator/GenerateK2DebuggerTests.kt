// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.fir.testGenerator

import org.jetbrains.kotlin.idea.compose.k2.debugger.test.cases.AbstractK2ComposeDebuggerEvaluationTest
import org.jetbrains.kotlin.idea.compose.k2.debugger.test.cases.AbstractK2IdeK1CodeClassLambdaComposeSteppingTest
import org.jetbrains.kotlin.idea.compose.k2.debugger.test.cases.AbstractK2IdeK1CodeComposeSteppingTest
import org.jetbrains.kotlin.idea.compose.k2.debugger.test.cases.AbstractK2IdeK2CodeComposeSteppingTest
import org.jetbrains.kotlin.idea.fir.debugger.evaluate.AbstractK2CodeFragmentAutoImportTest
import org.jetbrains.kotlin.idea.fir.debugger.evaluate.AbstractK2CodeFragmentCompletionHandlerTest
import org.jetbrains.kotlin.idea.fir.debugger.evaluate.AbstractK2CodeFragmentCompletionTest
import org.jetbrains.kotlin.idea.fir.debugger.evaluate.AbstractK2CodeFragmentHighlightingTest
import org.jetbrains.kotlin.idea.fir.debugger.evaluate.AbstractK2MultiplatformCodeFragmentCompletionTest
import org.jetbrains.kotlin.idea.k2.debugger.test.cases.AbstractInlineScopesAndK2IdeK2CodeEvaluateExpressionTest
import org.jetbrains.kotlin.idea.k2.debugger.test.cases.AbstractK2BreakpointApplicabilityTest
import org.jetbrains.kotlin.idea.k2.debugger.test.cases.AbstractK2ClassNameCalculatorTest
import org.jetbrains.kotlin.idea.k2.debugger.test.cases.AbstractK2FlowAsyncStackTraceTest
import org.jetbrains.kotlin.idea.k2.debugger.test.cases.AbstractK2IdeK1CodeBreakpointHighlightingTest
import org.jetbrains.kotlin.idea.k2.debugger.test.cases.AbstractK2IdeK1CodeContinuationStackTraceTest
import org.jetbrains.kotlin.idea.k2.debugger.test.cases.AbstractK2IdeK1CodeCoroutineDumpTest
import org.jetbrains.kotlin.idea.k2.debugger.test.cases.AbstractK2IdeK1CodeFileRankingTest
import org.jetbrains.kotlin.idea.k2.debugger.test.cases.AbstractK2IdeK1CodeKotlinSteppingTest
import org.jetbrains.kotlin.idea.k2.debugger.test.cases.AbstractK2IdeK1CodeKotlinVariablePrintingTest
import org.jetbrains.kotlin.idea.k2.debugger.test.cases.AbstractK2IdeK1CodeSuspendStackTraceTest
import org.jetbrains.kotlin.idea.k2.debugger.test.cases.AbstractK2IdeK1CodeXCoroutinesStackTraceTest
import org.jetbrains.kotlin.idea.k2.debugger.test.cases.AbstractK2IdeK1CoroutineViewJobHierarchyTest
import org.jetbrains.kotlin.idea.k2.debugger.test.cases.AbstractK2IdeK2CodeBreakpointHighlightingTest
import org.jetbrains.kotlin.idea.k2.debugger.test.cases.AbstractK2IdeK2CodeContinuationStackTraceTest
import org.jetbrains.kotlin.idea.k2.debugger.test.cases.AbstractK2IdeK2CodeCoroutineDumpTest
import org.jetbrains.kotlin.idea.k2.debugger.test.cases.AbstractK2IdeK2CodeFileRankingTest
import org.jetbrains.kotlin.idea.k2.debugger.test.cases.AbstractK2IdeK2CodeKotlinEvaluateExpressionTest
import org.jetbrains.kotlin.idea.k2.debugger.test.cases.AbstractK2IdeK2CodeKotlinSteppingPacketsNumberTest
import org.jetbrains.kotlin.idea.k2.debugger.test.cases.AbstractK2IdeK2CodeKotlinSteppingTest
import org.jetbrains.kotlin.idea.k2.debugger.test.cases.AbstractK2IdeK2CodeKotlinVariablePrintingTest
import org.jetbrains.kotlin.idea.k2.debugger.test.cases.AbstractK2IdeK2CodeSuspendStackTraceTest
import org.jetbrains.kotlin.idea.k2.debugger.test.cases.AbstractK2IdeK2CodeXCoroutinesStackTraceTest
import org.jetbrains.kotlin.idea.k2.debugger.test.cases.AbstractK2IdeK2CoroutineViewJobHierarchyTest
import org.jetbrains.kotlin.idea.k2.debugger.test.cases.AbstractK2IdeK2MultiplatformCodeKotlinEvaluateExpressionTest
import org.jetbrains.kotlin.idea.k2.debugger.test.cases.AbstractK2IndyLambdaKotlinSteppingTest
import org.jetbrains.kotlin.idea.k2.debugger.test.cases.AbstractK2KotlinExceptionFilterTest
import org.jetbrains.kotlin.idea.k2.debugger.test.cases.AbstractK2PositionManagerTest
import org.jetbrains.kotlin.idea.k2.debugger.test.cases.AbstractK2SelectExpressionForDebuggerTest
import org.jetbrains.kotlin.idea.k2.debugger.test.cases.AbstractK2SmartStepIntoTest
import org.jetbrains.kotlin.idea.parcelize.k2.debugger.test.cases.AbstractK2ParcelizeDebuggerEvaluationTest
import org.jetbrains.kotlin.test.TargetBackend
import org.jetbrains.kotlin.testGenerator.model.GroupCategory.CODE_INSIGHT
import org.jetbrains.kotlin.testGenerator.model.GroupCategory.COMPLETION
import org.jetbrains.kotlin.testGenerator.model.GroupCategory.DEBUGGER
import org.jetbrains.kotlin.testGenerator.model.MutableTWorkspace
import org.jetbrains.kotlin.testGenerator.model.Patterns
import org.jetbrains.kotlin.testGenerator.model.Patterns.KT
import org.jetbrains.kotlin.testGenerator.model.Patterns.KT_WITHOUT_DOTS
import org.jetbrains.kotlin.testGenerator.model.model
import org.jetbrains.kotlin.testGenerator.model.testClass
import org.jetbrains.kotlin.testGenerator.model.testGroup

internal fun MutableTWorkspace.generateK2DebuggerTests() {
    testGroup("jvm-debugger/test/k2", testDataPath = "../testData", category = DEBUGGER) {

        listOf(
            AbstractK2IdeK1CodeKotlinSteppingTest::class,
            AbstractK2IdeK2CodeKotlinSteppingTest::class,
            AbstractK2IndyLambdaKotlinSteppingTest::class,
        ).forEach {
            testClass(it) {
                model("stepping/stepIntoAndSmartStepInto", pattern = Patterns.KT_WITHOUT_DOTS, testMethodName = "doStepIntoTest", testClassName = "StepInto")
                model("stepping/stepIntoAndSmartStepInto", pattern = Patterns.KT_WITHOUT_DOTS, testMethodName = "doSmartStepIntoTest", testClassName = "SmartStepInto")
                model("stepping/stepInto", pattern = Patterns.KT_WITHOUT_DOTS, testMethodName = "doStepIntoTest", testClassName = "StepIntoOnly")
                model("stepping/stepOut", pattern = Patterns.KT_WITHOUT_DOTS, testMethodName = "doStepOutTest")
                model("stepping/stepOver", pattern = Patterns.KT_WITHOUT_DOTS, testMethodName = "doStepOverTest")
                model("stepping/filters", pattern = Patterns.KT_WITHOUT_DOTS, testMethodName = "doStepIntoTest")
                model("stepping/custom", pattern = Patterns.KT_WITHOUT_DOTS, testMethodName = "doCustomTest")
            }
        }
        testClass<AbstractK2IdeK2CodeKotlinEvaluateExpressionTest> {
            model("evaluation/singleBreakpoint", testMethodName = "doSingleBreakpointTest", targetBackend = TargetBackend.JVM)
            model("evaluation/multipleBreakpoints", testMethodName = "doMultipleBreakpointsTest", targetBackend = TargetBackend.JVM)
            model("evaluation/jvmMultiModule", testMethodName = "doJvmMultiModuleTest", targetBackend = TargetBackend.JVM)
        }

        testClass<AbstractK2IdeK2MultiplatformCodeKotlinEvaluateExpressionTest> {
            model("evaluation/multiplatform", testMethodName = "doMultipleBreakpointsTest", targetBackend = TargetBackend.JVM)
        }

        testClass<AbstractInlineScopesAndK2IdeK2CodeEvaluateExpressionTest> {
            model("evaluation/singleBreakpoint", testMethodName = "doSingleBreakpointTest", targetBackend = TargetBackend.JVM)
            model("evaluation/multipleBreakpoints", testMethodName = "doMultipleBreakpointsTest", targetBackend = TargetBackend.JVM)
        }

        testClass<AbstractK2SelectExpressionForDebuggerTest> {
            model("selectExpression")
        }


        testClass<AbstractK2PositionManagerTest> {
            model("positionManager", isRecursive = false, pattern = Patterns.KT, testClassName = "SingleFile")
            model("positionManager", isRecursive = false, pattern = Patterns.DIRECTORY, testClassName = "MultiFile")
        }

        listOf(AbstractK2IdeK1CodeBreakpointHighlightingTest::class, AbstractK2IdeK2CodeBreakpointHighlightingTest::class).forEach {
            testClass(it) {
                model("highlighting", isRecursive = false, pattern = KT_WITHOUT_DOTS, testMethodName = "doCustomTest")
            }
        }

        testClass<AbstractK2IdeK2CodeKotlinSteppingPacketsNumberTest> {
            model("stepping/packets", isRecursive = false, pattern = KT_WITHOUT_DOTS, testMethodName = "doCustomTest")
        }

        testClass<AbstractK2SmartStepIntoTest> {
            model("smartStepInto")
        }

        testClass<AbstractK2BreakpointApplicabilityTest> {
            model("breakpointApplicability")
        }

        listOf(AbstractK2IdeK1CodeFileRankingTest::class, AbstractK2IdeK2CodeFileRankingTest::class,).forEach {
            testClass(it) {
                model("fileRanking")
            }
        }

        listOf(AbstractK2IdeK1CodeSuspendStackTraceTest::class, AbstractK2IdeK2CodeSuspendStackTraceTest::class).forEach {
            testClass(it) {
                model("suspendStackTrace")
            }
        }

        testClass<AbstractK2FlowAsyncStackTraceTest> {
            model("asyncStackTrace/flows")
        }

        listOf(
            AbstractK2IdeK1CodeCoroutineDumpTest::class,
            AbstractK2IdeK2CodeCoroutineDumpTest::class,
        ).forEach {
            testClass(it) {
                model("coroutines")
            }
        }

        listOf(
            AbstractK2IdeK2CoroutineViewJobHierarchyTest::class,
            AbstractK2IdeK1CoroutineViewJobHierarchyTest::class,
        ).forEach {
            testClass(it) {
                model("coroutinesView")
            }
        }

        //testClass<AbstractSequenceTraceTestCase> { // TODO: implement mapping logic for terminal operations
        //    model("sequence/streams/sequence", excludedDirectories = listOf("terminal"))
        //}
        //
        //testClass<AbstractSequenceTraceWithIREvaluatorTestCase> { // TODO: implement mapping logic for terminal operations
        //    model("sequence/streams/sequence", excludedDirectories = listOf("terminal"))
        //}
        //
        listOf(
            AbstractK2IdeK1CodeContinuationStackTraceTest::class,
            AbstractK2IdeK2CodeContinuationStackTraceTest::class,
        ).forEach {
            testClass(it) {
                model("continuation")
            }
        }

        listOf(AbstractK2IdeK1CodeKotlinVariablePrintingTest::class, AbstractK2IdeK2CodeKotlinVariablePrintingTest::class,).forEach {
            testClass(it) {
                model("variables", isRecursive = false)
            }
        }

        listOf(
            AbstractK2IdeK1CodeXCoroutinesStackTraceTest::class,
            AbstractK2IdeK2CodeXCoroutinesStackTraceTest::class,
        ).forEach {
            testClass(it) {
                model("xcoroutines")
            }
        }

        testClass<AbstractK2ClassNameCalculatorTest> {
            model("classNameCalculator")
        }

        testClass<AbstractK2KotlinExceptionFilterTest> {
            model("exceptionFilter", pattern = Patterns.forRegex("""^([^.]+)$"""), isRecursive = false)
        }

    }

    testGroup("fir/tests", testDataPath = "../../completion/testData", category = COMPLETION) {
        testClass<AbstractK2CodeFragmentCompletionHandlerTest> {
            model("handlers/runtimeCast")
        }

        testClass<AbstractK2CodeFragmentCompletionTest> {
            model("basic/codeFragments", pattern = KT)
        }

        testClass<AbstractK2MultiplatformCodeFragmentCompletionTest> {
            model("basic/codeFragmentsMultiplatform", pattern = KT)
        }
    }

    testGroup("fir/tests", testDataPath = "../../idea/tests/testData", category = CODE_INSIGHT) {
        testClass<AbstractK2CodeFragmentHighlightingTest> {
            model("checker/codeFragments", pattern = KT, isRecursive = false)
            model("checker/codeFragments/imports", testMethodName = "doTestWithImport", pattern = KT)
        }

        testClass<AbstractK2CodeFragmentAutoImportTest> {
            model("quickfix.special/codeFragmentAutoImport", pattern = KT, isRecursive = false)
        }
    }
}

internal fun MutableTWorkspace.generateK2DebuggerTestsWithCompilerPlugins() {
    testGroup("jvm-debugger/test/compose", testDataPath = "../testData", category = DEBUGGER) {
        testClass<AbstractK2IdeK1CodeComposeSteppingTest> {
            model("stepping/compose")
        }
        testClass<AbstractK2IdeK2CodeComposeSteppingTest> {
            model("stepping/compose")
        }
        testClass<AbstractK2IdeK1CodeClassLambdaComposeSteppingTest> {
            model("stepping/compose")
        }
        testClass<AbstractK2ComposeDebuggerEvaluationTest> {
            model("evaluation/compose")
        }
    }
    testGroup("jvm-debugger/test/parcelize", testDataPath = "../testData", category = DEBUGGER) {
        testClass<AbstractK2ParcelizeDebuggerEvaluationTest> {
            model("evaluation/parcelize")
        }
    }
}
