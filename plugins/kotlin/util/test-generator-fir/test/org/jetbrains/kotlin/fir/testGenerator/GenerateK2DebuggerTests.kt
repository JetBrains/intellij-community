// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.fir.testGenerator

import org.jetbrains.kotlin.idea.compose.k2.debugger.test.cases.*
import org.jetbrains.kotlin.idea.fir.debugger.evaluate.*
import org.jetbrains.kotlin.idea.k2.debugger.test.cases.*
import org.jetbrains.kotlin.idea.parcelize.k2.debugger.test.cases.AbstractK2ParcelizeDebuggerEvaluationTest
import org.jetbrains.kotlin.test.TargetBackend
import org.jetbrains.kotlin.testGenerator.model.*
import org.jetbrains.kotlin.testGenerator.model.GroupCategory.*
import org.jetbrains.kotlin.testGenerator.model.Patterns.KT
import org.jetbrains.kotlin.testGenerator.model.Patterns.KT_WITHOUT_DOTS

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
            model("evaluation/singleBreakpoint", testMethodName = "doSingleBreakpointTest", targetBackend = TargetBackend.JVM_IR_WITH_IR_EVALUATOR)
            model("evaluation/multipleBreakpoints", testMethodName = "doMultipleBreakpointsTest", targetBackend = TargetBackend.JVM_IR_WITH_IR_EVALUATOR)
        }

        testClass<AbstractK2IdeK2MultiplatformCodeKotlinEvaluateExpressionTest> {
            model("evaluation/multiplatform", testMethodName = "doMultipleBreakpointsTest", targetBackend = TargetBackend.JVM_IR_WITH_IR_EVALUATOR)
        }

        testClass<AbstractInlineScopesAndK2IdeK2CodeEvaluateExpressionTest> {
            model("evaluation/singleBreakpoint", testMethodName = "doSingleBreakpointTest", targetBackend = TargetBackend.JVM_IR_WITH_IR_EVALUATOR)
            model("evaluation/multipleBreakpoints", testMethodName = "doMultipleBreakpointsTest", targetBackend = TargetBackend.JVM_IR_WITH_IR_EVALUATOR)
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
        testClass<AbstractK2ComposeSteppingTest> {
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