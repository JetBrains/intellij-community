package org.jetbrains.kotlin.idea.debugger.test

import com.intellij.debugger.engine.JavaValue
import com.intellij.debugger.engine.SourcePositionProvider
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.execution.process.ProcessOutputType
import org.jetbrains.kotlin.idea.debugger.invokeInManagerThread
import org.jetbrains.kotlin.idea.debugger.test.preference.DebuggerPreferences
import org.jetbrains.kotlin.idea.debugger.test.util.recursiveIterator
import org.jetbrains.kotlin.test.InTextDirectivesUtils.findLinesWithPrefixesRemoved
import org.jetbrains.kotlin.test.KotlinBaseTest

abstract class AbstractJumpToSourceTest : KotlinDescriptorTestCaseWithStepping() {
    override fun doMultiFileTest(files: TestFiles, preferences: DebuggerPreferences) {
        val variableNames = loadVariableNames(files.wholeFile)
        doOnBreakpoint {
            printSourcePositions(this, variableNames)
        }
    }

    private fun printSourcePositions(suspendContext: SuspendContextImpl, variableNames: Set<String>) =
        processStackFrameOnPooledThread {
            val variables = recursiveIterator().asSequence().filterIsInstance<JavaValue>().toList()
            printSourcePositions(variables, variableNames)
            suspendContext.invokeInManagerThread { resume(suspendContext) }
        }


    private fun printSourcePositions(variables: List<JavaValue>, variableNames: Set<String>) {
        for (variable in variables) {
            val fullName = variable.fullName()
            if (fullName in variableNames) {
                val sourcePosition = debugProcess.invokeInManagerThread { debuggerContext ->
                    SourcePositionProvider.getSourcePosition(
                        variable.descriptor,
                        debugProcess.project,
                        debuggerContext
                    )
                } ?: error("Failed to calculate source position for $fullName")
                val message = "Source position of $fullName is ${sourcePosition.line + 1}\n"
                print(message, ProcessOutputType.SYSTEM)
            }
        }
    }

    private fun loadVariableNames(testFile: KotlinBaseTest.TestFile) =
        findLinesWithPrefixesRemoved(testFile.content, "// JUMP TO SOURCE: ").toSet()

    private fun JavaValue.fullName(): String {
        val nodeNames = mutableListOf(name)
        var node = parent
        while (node != null) {
            nodeNames.add(node.name)
            node = node.parent
        }

        return nodeNames.reversed().joinToString(".")
    }
}
