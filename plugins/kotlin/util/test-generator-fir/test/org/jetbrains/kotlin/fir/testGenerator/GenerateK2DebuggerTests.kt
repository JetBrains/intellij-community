// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.fir.testGenerator

import org.jetbrains.kotlin.idea.k2.debugger.test.cases.*
import org.jetbrains.kotlin.testGenerator.model.*

internal fun MutableTWorkspace.generateK2DebuggerTests() {
    testGroup("jvm-debugger/test/k2", testDataPath = "../testData") {

        testClass<AbstractK2IrKotlinSteppingTest> {
            model("stepping/stepIntoAndSmartStepInto", pattern = Patterns.KT_WITHOUT_DOTS, testMethodName = "doStepIntoTest", testClassName = "StepInto")
            model("stepping/stepIntoAndSmartStepInto", pattern = Patterns.KT_WITHOUT_DOTS, testMethodName = "doSmartStepIntoTest", testClassName = "SmartStepInto")
            model("stepping/stepInto", pattern = Patterns.KT_WITHOUT_DOTS, testMethodName = "doStepIntoTest", testClassName = "StepIntoOnly")
            model("stepping/stepOut", pattern = Patterns.KT_WITHOUT_DOTS, testMethodName = "doStepOutTest")
            model("stepping/stepOver", pattern = Patterns.KT_WITHOUT_DOTS, testMethodName = "doStepOverTest")
            model("stepping/filters", pattern = Patterns.KT_WITHOUT_DOTS, testMethodName = "doStepIntoTest")
            model("stepping/custom", pattern = Patterns.KT_WITHOUT_DOTS, testMethodName = "doCustomTest")
        }
        //
        //testClass<AbstractKotlinEvaluateExpressionTest> {
        //    model("evaluation/singleBreakpoint", testMethodName = "doSingleBreakpointTest", targetBackend = TargetBackend.JVM_WITH_OLD_EVALUATOR)
        //    model("evaluation/multipleBreakpoints", testMethodName = "doMultipleBreakpointsTest", targetBackend = TargetBackend.JVM_WITH_OLD_EVALUATOR)
        //}
        //
        //testClass<AbstractIrKotlinEvaluateExpressionTest> {
        //    model("evaluation/singleBreakpoint", testMethodName = "doSingleBreakpointTest", targetBackend = TargetBackend.JVM_IR_WITH_OLD_EVALUATOR)
        //    model("evaluation/multipleBreakpoints", testMethodName = "doMultipleBreakpointsTest", targetBackend = TargetBackend.JVM_IR_WITH_OLD_EVALUATOR)
        //}
        //
        //testClass<AbstractIrKotlinEvaluateExpressionWithIRFragmentCompilerTest> {
        //    model("evaluation/singleBreakpoint", testMethodName = "doSingleBreakpointTest", targetBackend = TargetBackend.JVM_IR_WITH_IR_EVALUATOR)
        //    model("evaluation/multipleBreakpoints", testMethodName = "doMultipleBreakpointsTest", targetBackend = TargetBackend.JVM_IR_WITH_IR_EVALUATOR)
        //}
        //
        //testClass<AbstractKotlinEvaluateExpressionInMppTest> {
        //    model("evaluation/singleBreakpoint", testMethodName = "doSingleBreakpointTest", targetBackend = TargetBackend.JVM_IR_WITH_OLD_EVALUATOR)
        //    model("evaluation/multipleBreakpoints", testMethodName = "doMultipleBreakpointsTest", targetBackend = TargetBackend.JVM_IR_WITH_OLD_EVALUATOR)
        //    model("evaluation/multiplatform", testMethodName = "doMultipleBreakpointsTest", targetBackend = TargetBackend.JVM_IR_WITH_IR_EVALUATOR)
        //}
        //

        testClass<AbstractK2SelectExpressionForDebuggerTest> {
            model("selectExpression")
        }


        testClass<AbstractK2PositionManagerTest> {
            model("positionManager", isRecursive = false, pattern = Patterns.KT, testClassName = "SingleFile")
            model("positionManager", isRecursive = false, pattern = Patterns.DIRECTORY, testClassName = "MultiFile")
        }

        testClass<AbstractK2SmartStepIntoTest> {
            model("smartStepInto")
        }

        testClass<AbstractK2BreakpointApplicabilityTest> {
            model("breakpointApplicability")
        }


        testClass<AbstractK2FileRankingTest> {
            model("fileRanking")
        }


        testClass<AbstractK2AsyncStackTraceTest> {
            model("asyncStackTrace")
        }

        //
        //testClass<AbstractCoroutineDumpTest> {
        //    model("coroutines")
        //}
        //
        //testClass<AbstractSequenceTraceTestCase> { // TODO: implement mapping logic for terminal operations
        //    model("sequence/streams/sequence", excludedDirectories = listOf("terminal"))
        //}
        //
        //testClass<AbstractSequenceTraceWithIREvaluatorTestCase> { // TODO: implement mapping logic for terminal operations
        //    model("sequence/streams/sequence", excludedDirectories = listOf("terminal"))
        //}
        //
        //testClass<AbstractContinuationStackTraceTest> {
        //    model("continuation")
        //}

        testClass<AbstractK2KotlinVariablePrintingTest> {
            model("variables")
        }

        //testClass<AbstractXCoroutinesStackTraceTest> {
        //    model("xcoroutines")
        //}

        testClass<AbstractK2ClassNameCalculatorTest> {
            model("classNameCalculator")
        }

        testClass<AbstractK2KotlinExceptionFilterTest> {
            model("exceptionFilter", pattern = Patterns.forRegex("""^([^.]+)$"""), isRecursive = false)
        }

    }
}