// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.test

import com.intellij.debugger.engine.ContextUtil
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.evaluation.CodeFragmentKind
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl
import com.intellij.debugger.engine.evaluation.expression.EvaluatorBuilderImpl
import com.intellij.debugger.impl.DebuggerContextImpl
import com.intellij.debugger.impl.DebuggerContextImpl.createDebuggerContext
import com.intellij.debugger.impl.DebuggerUtilsImpl
import com.intellij.debugger.memory.utils.InstanceJavaValue
import com.intellij.debugger.memory.utils.InstanceValueDescriptor
import com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.treeStructure.Tree
import com.intellij.xdebugger.XDebuggerTestUtil
import com.intellij.xdebugger.impl.ui.tree.ValueMarkup
import com.sun.jdi.ObjectReference
import org.jetbrains.eval4j.ObjectValue
import org.jetbrains.eval4j.Value
import org.jetbrains.eval4j.jdi.asValue
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.debugger.evaluate.KotlinCodeFragmentFactory
import org.jetbrains.kotlin.idea.debugger.test.preference.DebuggerPreferenceKeys
import org.jetbrains.kotlin.idea.debugger.test.preference.DebuggerPreferences
import org.jetbrains.kotlin.idea.debugger.test.util.FramePrinter
import org.jetbrains.kotlin.idea.debugger.test.util.FramePrinterDelegate
import org.jetbrains.kotlin.idea.debugger.test.util.KotlinOutputChecker
import org.jetbrains.kotlin.idea.debugger.test.util.SteppingInstruction
import org.jetbrains.kotlin.idea.test.InTextDirectivesUtils.findLinesWithPrefixesRemoved
import org.jetbrains.kotlin.idea.test.InTextDirectivesUtils.findStringWithPrefixes
import org.jetbrains.kotlin.idea.test.KotlinBaseTest
import org.jetbrains.kotlin.test.utils.IgnoreTests
import java.io.File
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import javax.swing.tree.TreeNode

private data class CodeFragment(val text: String, val result: String, val kind: CodeFragmentKind)

private data class DebugLabel(val name: String, val localName: String)

private class EvaluationTestData(
    val instructions: List<SteppingInstruction>,
    val fragments: List<CodeFragment>,
    val debugLabels: List<DebugLabel>
)

abstract class AbstractKotlinEvaluateExpressionTest : KotlinDescriptorTestCaseWithStepping(), FramePrinterDelegate {
    private companion object {
        private val ID_PART_REGEX = "id=[0-9]*".toRegex()
        private const val IGNORE_OLD_BACKEND_DIRECTIVE = "// IGNORE_OLD_BACKEND"
    }

    override val debuggerContext: DebuggerContextImpl
        get() = super.debuggerContext

    private var isMultipleBreakpointsTest = false
    private var isFrameTest = false

    override fun setUp() {
        super.setUp()
        atDebuggerTearDown { exceptions.clear() }
    }

    override fun fragmentCompilerBackend() =
        FragmentCompilerBackend.JVM

    private val exceptions = ConcurrentHashMap<String, Throwable>()

    fun doSingleBreakpointTest(path: String) {
        isMultipleBreakpointsTest = false
        doTest(path)
    }

    override fun doTest(unused: String) =
        if (useIrBackend()) {
            super.doTest(unused)
        } else {
            // Consider ignoring old backend tests when testing the
            // debugger interaction with new language features
            IgnoreTests.runTestIfNotDisabledByFileDirective(
                Paths.get(unused),
                IGNORE_OLD_BACKEND_DIRECTIVE,
                directivePosition = IgnoreTests.DirectivePosition.LAST_LINE_IN_FILE,
            ) {
                super.doTest(unused)
            }
        }

    fun doMultipleBreakpointsTest(path: String) {
        isMultipleBreakpointsTest = true
        doTest(path)
    }

    override fun doMultiFileTest(files: TestFiles, preferences: DebuggerPreferences) {
        val wholeFile = files.wholeFile

        val instructions = SteppingInstruction.parse(wholeFile)
        val expressions = loadExpressions(wholeFile)
        val blocks = loadCodeBlocks(files.originalFile)
        val debugLabels = loadDebugLabels(wholeFile)

        val data = EvaluationTestData(instructions, expressions + blocks, debugLabels)

        isFrameTest = preferences[DebuggerPreferenceKeys.PRINT_FRAME]

        if (isMultipleBreakpointsTest) {
            performMultipleBreakpointTest(data)
        } else {
            performSingleBreakpointTest(data)
        }
    }

    private fun performSingleBreakpointTest(data: EvaluationTestData) {
        process(data.instructions)

        doOnBreakpoint {
            createDebugLabels(data.debugLabels)

            for ((expression, expected, kind) in data.fragments) {
                mayThrow(expression) {
                    evaluate(this, expression, kind, expected)
                }
            }

            printFrame(this) {
                resume(this)
            }
        }

        finish()
    }

    private fun performMultipleBreakpointTest(data: EvaluationTestData) {
        for ((expression, expected) in data.fragments) {
            doOnBreakpoint {
                mayThrow(expression) {
                    try {
                        evaluate(this, expression, CodeFragmentKind.EXPRESSION, expected)
                    } finally {
                        printFrame(this) { resume(this) }
                    }
                }
            }
        }
        finish()
    }

    private fun printFrame(suspendContext: SuspendContextImpl, completion: () -> Unit) {
        if (!isFrameTest) {
            completion()
            return
        }

        processStackFramesOnPooledThread {
            for (stackFrame in this) {
                val result = FramePrinter(suspendContext).print(stackFrame)
                print(result, ProcessOutputTypes.SYSTEM)
            }
            suspendContext.invokeInManagerThread(completion)
        }
    }

    override fun evaluate(suspendContext: SuspendContextImpl, textWithImports: TextWithImportsImpl) {
        evaluate(suspendContext, textWithImports, null)
    }

    private fun evaluate(suspendContext: SuspendContextImpl, text: String, codeFragmentKind: CodeFragmentKind, expectedResult: String?) {
        val textWithImports = TextWithImportsImpl(codeFragmentKind, text, "", KotlinFileType.INSTANCE)
        return evaluate(suspendContext, textWithImports, expectedResult)
    }

    private fun evaluate(suspendContext: SuspendContextImpl, item: TextWithImportsImpl, expectedResult: String?) {
        val evaluationContext = this.evaluationContext
        val sourcePosition = ContextUtil.getSourcePosition(suspendContext)

        // Default test debuggerContext doesn't provide a valid stackFrame so we have to create one more for evaluation purposes.
        val frameProxy = suspendContext.frameProxy
        val threadProxy = frameProxy?.threadProxy()
        val debuggerContext = createDebuggerContext(myDebuggerSession, suspendContext, threadProxy, frameProxy)
        debuggerContext.initCaches()

        val contextElement = ContextUtil.getContextElement(debuggerContext)!!

        assert(KotlinCodeFragmentFactory().isContextAccepted(contextElement)) {
            val text = runReadAction { contextElement.text }
            "KotlinCodeFragmentFactory should be accepted for context element otherwise default evaluator will be called. " +
                    "ContextElement = $text"
        }

        contextElement.putCopyableUserData(KotlinCodeFragmentFactory.DEBUG_CONTEXT_FOR_TESTS, debuggerContext)

        suspendContext.runActionInSuspendCommand {
            try {
                val evaluator = runReadAction {
                    EvaluatorBuilderImpl.build(
                        item,
                        contextElement,
                        sourcePosition,
                        this@AbstractKotlinEvaluateExpressionTest.project
                    )
                }

                val value = evaluator.evaluate(evaluationContext)
                val actualResult = value.asValue().asString()
                if (expectedResult != null) {
                    assertEquals(
                        "Evaluate expression returns wrong result for ${item.text}:\n" +
                                "expected = $expectedResult\n" +
                                "actual   = $actualResult\n",
                        expectedResult, actualResult
                    )
                }
            } catch (e: EvaluateException) {
                val expectedMessage = e.message?.replaceFirst(
                    ID_PART_REGEX,
                    "id=ID"
                )
                assertEquals(
                    "Evaluate expression throws wrong exception for ${item.text}:\n" +
                            "expected = $expectedResult\n" +
                            "actual   = $expectedMessage\n",
                    expectedResult,
                    expectedMessage
                )
            }
        }
    }

    override fun logDescriptor(descriptor: NodeDescriptorImpl, text: String) {
        super.logDescriptor(descriptor, text)
    }

    override fun expandAll(tree: Tree, runnable: () -> Unit, filter: (TreeNode) -> Boolean, suspendContext: SuspendContextImpl) {
        super.expandAll(tree, runnable, HashSet(), filter, suspendContext)
    }

    private fun mayThrow(expression: String, f: () -> Unit) {
        try {
            f()
        } catch (e: Throwable) {
            exceptions[expression] = e
        }
    }

    override fun throwExceptionsIfAny() {
        super.throwExceptionsIfAny()
        if (exceptions.isNotEmpty()) {
            if (!isTestIgnored()) {
                for (exc in exceptions.values) {
                    exc.printStackTrace()
                }
                val expressionsText = exceptions.entries.joinToString("\n") { (k, v) -> "expression: $k, exception: ${v.message}" }
                throw AssertionError("Test failed:\n$expressionsText")
            } else {
                (checker as KotlinOutputChecker).threwException = true
            }
        }
    }

    private fun Value.asString(): String {
        if (this is ObjectValue && this.value is ObjectReference) {
            return this.toString().replaceFirst(ID_PART_REGEX, "id=ID")
        }
        return this.toString()
    }

    private fun loadExpressions(testFile: KotlinBaseTest.TestFile): List<CodeFragment> {
        val directives = findLinesWithPrefixesRemoved(testFile.content, "// EXPRESSION: ")
        val expected = findLinesWithPrefixesRemoved(testFile.content, "// RESULT: ")
        assert(directives.size == expected.size) { "Sizes of test directives are different" }
        return directives.zip(expected).map { (text, result) -> CodeFragment(text, result, CodeFragmentKind.EXPRESSION) }
    }

    private fun loadCodeBlocks(wholeFile: File): List<CodeFragment> {
        val regexp = (Regex.escape(wholeFile.name) + ".fragment\\d*").toRegex()
        val fragmentFiles = wholeFile.parentFile.listFiles { _, name -> regexp.matches(name) } ?: emptyArray()

        val codeFragments = mutableListOf<CodeFragment>()

        for (fragmentFile in fragmentFiles) {
            val contents = FileUtil.loadFile(fragmentFile, true)
            val value = findStringWithPrefixes(contents, "// RESULT: ") ?: error("'RESULT' directive is missing in $fragmentFile")
            codeFragments += CodeFragment(contents, value, CodeFragmentKind.CODE_BLOCK)
        }

        return codeFragments
    }

    private fun loadDebugLabels(testFile: KotlinBaseTest.TestFile): List<DebugLabel> {
        return findLinesWithPrefixesRemoved(testFile.content, "// DEBUG_LABEL: ")
            .map { text ->
                val labelParts = text.split("=")
                assert(labelParts.size == 2) { "Wrong format for DEBUG_LABEL directive: // DEBUG_LABEL: {localVariableName} = {labelText}" }

                val localName = labelParts[0].trim()
                val name = labelParts[1].trim()
                DebugLabel(name, localName)
            }
    }

    private fun createDebugLabels(labels: List<DebugLabel>) {
        if (labels.isEmpty()) {
            return
        }

        val valueMarkers = DebuggerUtilsImpl.getValueMarkers(debugProcess)

        for ((name, localName) in labels) {
            val localVariable = evaluationContext.frameProxy!!.visibleVariableByName(localName)
            assert(localVariable != null) { "Cannot find localVariable for label: name = $localName" }

            val localVariableValue = evaluationContext.frameProxy!!.getValue(localVariable) as? ObjectReference
            assert(localVariableValue != null) { "Local variable $localName should be an ObjectReference" }

            // just need a wrapper XValue to pass into markValue
            val instanceValue = InstanceJavaValue(
                InstanceValueDescriptor(project, localVariableValue),
                evaluationContext,
                debugProcess.xdebugProcess?.nodeManager
            )
            XDebuggerTestUtil.markValue(valueMarkers, instanceValue, ValueMarkup(name, null, name))
        }
    }
}
