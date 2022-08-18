// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.test.dex

import com.jetbrains.jdi.MockLocalVariable
import com.sun.jdi.LocalVariable
import com.sun.jdi.Location
import com.sun.jdi.Method
import com.sun.jdi.StackFrame
import junit.framework.TestCase
import org.jetbrains.kotlin.idea.debugger.core.sortedVariablesWithLocation
import org.jetbrains.kotlin.idea.debugger.core.stackFrame.computeKotlinStackFrameInfos
import org.jetbrains.kotlin.idea.debugger.test.mock.MockMethodInfo
import org.jetbrains.kotlin.idea.debugger.test.mock.MockStackFrame
import org.jetbrains.kotlin.idea.debugger.test.mock.MockVirtualMachine

abstract class AbstractLocalVariableTableTest : TestCase() {
    private fun LocalVariable.asString(): String =
        "${name()}: ${typeName()}"

    private fun printLocalVariableTable(locals: List<LocalVariable>): String =
        locals.joinToString("\n") { it.asString() }

    // LVT heuristic regression test.
    //
    // Test that local variable tables are the same on jvm and dex after sorting
    // variables and removing spilled variables. In this case the rest of the debugger
    // works with the same view of the local variable table, which makes this a
    // sufficient criterion for correctness.
    //
    // However, in many cases this is too strong, since variables may appear in
    // different order between individual lines. Such differences are not observable,
    // but would cause test failures.
    protected fun doLocalVariableTableComparisonTest(
        jvm: MockMethodInfo,
        dex: MockMethodInfo,
    ) {
        val jvmLocals = jvm.toMockMethod(MockVirtualMachine("JVM Mock"))
            .sortedVariablesWithLocation().map { it.variable }

        // Filter repeated appearances of the same variable in the dex LVT.
        val dexLocals = dex.toMockMethod(MockVirtualMachine("Dalvik"))
            .sortedVariablesWithLocation().map {
                it.variable as MockLocalVariable
            }
        val filteredDexLocals =
            dexLocals
                .zip(dexLocals.drop(1))
                .mapNotNullTo(mutableListOf(dexLocals.first())) { (prev, next) ->
                    next.takeIf {
                        prev.name() != next.name()
                                || prev.signature() != next.signature()
                                || prev.startPc + prev.length != next.startPc
                    }
                }

        assertEquals(printLocalVariableTable(jvmLocals), printLocalVariableTable(filteredDexLocals))
    }

    // Compare local variable tables, while ignoring differences in the order of
    // variables that start on the same code index on the JVM. The debugger cannot
    // rely on the order of these variables anyway and on dex the variables will start
    // on different lines.
    private fun compareLocals(jvm: List<MockLocalVariable>, dex: List<MockLocalVariable>) {
        val blocks = jvm.groupBy { it.startPc }.entries
            .sortedBy { it.key }.map { it.value.map { variable -> variable.asString() } }
        val iter = dex.iterator()
        for (block in blocks) {
            val target = block.toMutableList()
            while (target.isNotEmpty()) {
                assert(iter.hasNext()) {
                    "Missing variables in dex LVT: ${block.joinToString()}"
                }
                val nextVariable = iter.next().asString()
                assert(nextVariable in target) {
                    "Unexpected variable in dex LVT: $nextVariable, expected: ${block.joinToString()}"
                }
                target.remove(nextVariable)
            }
        }
        assert(!iter.hasNext()) {
            "Extra variable in dex LVT: ${iter.next().name()}"
        }
    }

    private data class BreakpointLocation(val source: String, val line: Int) {
        constructor(location: Location) : this(location.sourceName() ?: "", location.lineNumber())
        override fun toString(): String = "$source:$line"
    }

    // With a few exceptions, we can only set breakpoints on the first occurrence of a
    // given line in the debugger. This method returns a map from file, line pairs
    // to the Location that would be used for a breakpoint request on this line.
    private fun extractBreakpoints(allLineLocations: List<Location>): Map<BreakpointLocation, Location> =
        allLineLocations
            .groupBy(AbstractLocalVariableTableTest::BreakpointLocation)
            .filterKeys { it.source != "fake.kt" }
            .mapValues { (_, locations) ->
                locations.minByOrNull { it.codeIndex() }!!
            }

    // Call [block] with each matching pair of jvm and dex breakpoint locations.
    // With the same caveats as [extractBreakpoints] above.
    private fun forEachMatchingBreakpoint(jvm: Method, dex: Method, block: (StackFrame, StackFrame) -> Unit) {
        val jvmBreakpoints = extractBreakpoints(jvm.allLineLocations())
        val dexBreakpoints = extractBreakpoints(dex.allLineLocations())
        for ((breakpoint, jvmLocation) in jvmBreakpoints.entries) {
            assert(breakpoint in dexBreakpoints.keys) {
                "Breakpoint $breakpoint@${jvmLocation.lineNumber("Java")} not found in dex"
            }

            val dexLocation = dexBreakpoints.getValue(breakpoint)
            block(MockStackFrame(jvmLocation), MockStackFrame(dexLocation))
        }
        dexBreakpoints.keys.forEach { breakpoint ->
            assert(breakpoint in jvmBreakpoints.keys) {
                "Breakpoint $breakpoint not found in jvm"
            }
        }
    }

    // LVT heuristic regression tests on breakpoints.
    //
    // Test that the visible local variables in dex and jvm code are the same on
    // every possible breakpoint. This is sufficient for the debugger to behave
    // the same on dex as on the jvm.
    //
    // This test can fail in the following cases:
    //
    // - Due to spill code and block reordering we sometimes can't distinguish
    //   between register spilling and newly introduced variables with the same
    //   name. In this case our heuristics simply fail.
    // - Due to block reordering we can select different breakpoints on dex than
    //   on the jvm, since the chosen breakpoints depend on code indices.
    //
    // The second failure case is an artifact of how we identify breakpoints.
    // In the first case we may still be able to compute correct inline call stacks
    // so long as no scope introduction variable is reordered with respect to other
    // scope introduction variables and no inlined variable is moved outside
    // its original scope.
    //
    // ---
    //
    // Apart from the above, this test can also fail if we are missing line numbers
    // on dex or have superfluous lines on dex. Either case would be a bug in D8, not
    // in the Kotlin compiler or debugger.
    protected fun doLocalVariableTableBreakpointComparisonTest(jvm: MockMethodInfo, dex: MockMethodInfo) {
        val jvmMethod = jvm.toMockMethod(MockVirtualMachine("JVM Mock"))
        val dexMethod = dex.toMockMethod(MockVirtualMachine("Dalvik"))

        val jvmVariables = jvmMethod.sortedVariablesWithLocation().map { it.variable as MockLocalVariable }
        val dexVariables = dexMethod.sortedVariablesWithLocation().map { it.variable as MockLocalVariable }

        forEachMatchingBreakpoint(jvmMethod, dexMethod) { jvmStackFrame, dexStackFrame ->
            val jvmLocals = jvmVariables.filter { it.isVisible(jvmStackFrame) }
            val dexLocals = dexVariables.filter { it.isVisible(dexStackFrame) }

            try {
                compareLocals(jvmLocals, dexLocals)
            } catch (e: AssertionError) {
                val dexLocalVariables = printLocalVariableTable(dexLocals)
                val jvmLocalVariables = printLocalVariableTable(jvmLocals)
                assertEquals(
                    e.message,
                    "${jvmStackFrame.location()}\n$jvmLocalVariables",
                    "${dexStackFrame.location()}\n$dexLocalVariables"
                )
            }
        }
    }

    // Print the Kotlin stack frame info for the given stack frame
    private fun printStackFrame(stackFrame: StackFrame): String =
        stackFrame.computeKotlinStackFrameInfos().joinToString("\n") { info ->
            val location = info.callLocation?.let {
                "${it.sourceName()}:${it.lineNumber()}"
            } ?: "<no location>"
            val header = "${info.displayName ?: stackFrame.location().method().name()} at $location depth ${info.depth}:\n"
            val variables = info.visibleVariables.sortedBy { it.asString() }.joinToString("\n") { variable ->
                "  ${variable.name()}: ${variable.signature()}"
            }
            header + variables
        }

    // Inline call stack regression test on breakpoints.
    //
    // Test that the inline call stacks in dex and jvm code are the same up to the
    // order of variables in each frame on every possible breakpoint.
    //
    // The same caveats as for `doLocalVariableTableBreakpointComparisonTest` apply.
    protected fun doInlineCallStackComparisonTest(jvm: MockMethodInfo, dex: MockMethodInfo) {
        val jvmMethod = jvm.toMockMethod(MockVirtualMachine("JVM Mock"))
        val dexMethod = dex.toMockMethod(MockVirtualMachine("Dalvik"))
        forEachMatchingBreakpoint(jvmMethod, dexMethod) { jvmStackFrame, dexStackFrame ->
            assertEquals(printStackFrame(jvmStackFrame), printStackFrame(dexStackFrame))
        }
    }

    // Manual inline call stack test for a fixed location.
    protected fun doKotlinInlineStackTest(
        codeIndex: Int,
        methodInfo: MockMethodInfo,
        expectation: String,
    ) {
        val method = methodInfo.toMockMethod(MockVirtualMachine("JVM Mock"))
        val location = method.allLineLocations().first {
            it.codeIndex().toInt() == codeIndex
        }
        assertEquals(expectation, printStackFrame(MockStackFrame(location)))
    }
}
