// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.test

import com.intellij.debugger.engine.ContextUtil
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.evaluation.CodeFragmentFactory
import com.intellij.debugger.engine.evaluation.CodeFragmentKind
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl
import com.intellij.debugger.engine.evaluation.expression.EvaluatorBuilderImpl
import com.intellij.debugger.engine.events.SuspendContextCommandImpl
import com.intellij.debugger.impl.DebuggerContextImpl
import com.intellij.debugger.impl.DebuggerContextImpl.createDebuggerContext
import com.intellij.debugger.impl.DebuggerUtilsImpl
import com.intellij.debugger.memory.utils.InstanceJavaValue
import com.intellij.debugger.memory.utils.InstanceValueDescriptor
import com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.extensions.ExtensionPoint
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.registerExtension
import com.intellij.ui.treeStructure.Tree
import com.intellij.xdebugger.XDebuggerTestUtil
import com.intellij.xdebugger.impl.ui.tree.ValueMarkup
import com.sun.jdi.ObjectReference
import com.sun.jdi.request.EventRequest
import junit.framework.TestCase
import org.jetbrains.eval4j.ObjectValue
import org.jetbrains.eval4j.Value
import org.jetbrains.eval4j.jdi.asValue
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils.findLinesWithPrefixesRemoved
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils.findStringWithPrefixes
import org.jetbrains.kotlin.idea.compilerPlugin.kotlinxSerialization.KotlinSerializationEnabledChecker
import org.jetbrains.kotlin.idea.debugger.core.CodeFragmentContextTuner
import org.jetbrains.kotlin.idea.debugger.evaluate.DebugContextProvider
import org.jetbrains.kotlin.idea.debugger.test.preference.DebuggerPreferenceKeys
import org.jetbrains.kotlin.idea.debugger.test.preference.DebuggerPreferences
import org.jetbrains.kotlin.idea.debugger.test.util.FramePrinter
import org.jetbrains.kotlin.idea.debugger.test.util.FramePrinterDelegate
import org.jetbrains.kotlin.idea.debugger.test.util.KotlinOutputChecker
import org.jetbrains.kotlin.idea.debugger.test.util.SteppingInstruction
import org.jetbrains.kotlin.idea.test.KotlinBaseTest
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.swing.tree.TreeNode

private data class CodeFragment(val text: String, val result: String, val kind: CodeFragmentKind)

private data class DebugLabel(val name: String, val localName: String)

private class EvaluationTestData(
    val instructions: List<SteppingInstruction>,
    val fragments: List<CodeFragment>,
    val debugLabels: List<DebugLabel>
)

abstract class AbstractIrKotlinEvaluateExpressionTest : KotlinDescriptorTestCaseWithStepping(), FramePrinterDelegate {
    private companion object {
        private val ID_PART_REGEX = "id=[0-9]*".toRegex()
        private val NON_WORD_REGEX = "\\W".toRegex()
    }

    override val debuggerContext: DebuggerContextImpl
        get() = super.debuggerContext

    private var isMultipleBreakpointsTest = false
    private var isFrameTest = false

    private var originalEnableKotlinEvaluatorInJavaContext: Boolean = false

    override fun setUp() {
        super.setUp()
        allowEvaluationInJavaContext()
        // Serialization compiler plugin may completely break IR evaluator (happened twice),
        // having it enabled in tests will help prevent the problem
        enableSerializationChecker()
        atDebuggerTearDown { exceptions.clear() }
        atDebuggerTearDown { restoreEvaluationInJavaContext() }
    }

    private fun allowEvaluationInJavaContext() {
        Registry.get("debugger.enable.kotlin.evaluator.in.java.context").let {
            originalEnableKotlinEvaluatorInJavaContext = it.asBoolean()
            it.setValue(true)
        }
    }

    private fun restoreEvaluationInJavaContext() {
        Registry.get("debugger.enable.kotlin.evaluator.in.java.context")
          .setValue(originalEnableKotlinEvaluatorInJavaContext)
    }

    private fun enableSerializationChecker() {
        ApplicationManager.getApplication().apply {
            if (!extensionArea.hasExtensionPoint(KotlinSerializationEnabledChecker.EP_NAME.name)) {
                extensionArea.registerExtensionPoint(
                    KotlinSerializationEnabledChecker.EP_NAME.name,
                    KotlinSerializationEnabledChecker::class.java.name,
                    ExtensionPoint.Kind.INTERFACE,
                    false
                )
            }
            registerExtension(KotlinSerializationEnabledChecker.EP_NAME, AlwaysYesForEvaluator, project)
        }
    }

    private val exceptions = ConcurrentHashMap<String, Throwable>()

    fun doSingleBreakpointTest(path: String) {
        isMultipleBreakpointsTest = false
        doTest(path)
    }

    fun doJvmMultiModuleTest(path: String) {
        isMultipleBreakpointsTest = false
        doTest(path)
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
                    if (myWasUsedOnlyDefaultSuspendPolicy) {
                        // It would be more correct to extract the policy from the breakpoint declaration (in test),
                        // but it is not a very easy task
                        TestCase.assertEquals("Invalid suspend policy on breakpoint", EventRequest.SUSPEND_ALL, suspendPolicy, )
                    }
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
            assert(debugProcess.isAttached)
            debugProcess.managerThread.schedule(object : SuspendContextCommandImpl(suspendContext) {
                override fun contextAction(suspendContext: SuspendContextImpl) {
                    completion()
                }
                override fun commandCancelled() = error(message = "Test was cancelled")
            })
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
        val frameProxy = getFrameProxy(suspendContext)
        val threadProxy = frameProxy?.threadProxy()
        val debuggerContext = createDebuggerContext(myDebuggerSession, suspendContext, threadProxy, frameProxy)
        debuggerContext.initCaches()

        val contextElement = runReadAction {
            CodeFragmentContextTuner.getInstance().tuneContextElement(ContextUtil.getContextElement(debuggerContext))!!
        }

        val codeFragmentFactory = CodeFragmentFactory.EXTENSION_POINT_NAME.extensions
            .first { it.fileType == KotlinFileType.INSTANCE }

        assert(codeFragmentFactory.isContextAccepted(contextElement)) {
            val text = runReadAction { contextElement.text }
            "KotlinCodeFragmentFactory should be accepted for context element otherwise default evaluator will be called. " +
                    "ContextElement = $text"
        }

        DebugContextProvider.supplyTestDebugContext(contextElement, debuggerContext)

        suspendContext.runActionInSuspendCommand {
            try {
                val evaluator = runReadAction {
                    EvaluatorBuilderImpl.build(
                        item,
                        contextElement,
                        sourcePosition,
                        this@AbstractIrKotlinEvaluateExpressionTest.project
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
                val actualMessage = e.message?.replaceFirst(
                    ID_PART_REGEX,
                    "id=ID"
                )

                // Remove any non-word characters to allow trivial differences in punctuation.
                assertEquals(
                    "Evaluate expression throws wrong exception for ${item.text}:\n" +
                            "expected = $expectedResult\n" +
                            "actual   = $actualMessage\n",
                    expectedResult?.replace(NON_WORD_REGEX, ""),
                    actualMessage?.replace(NON_WORD_REGEX, ""),
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
        val expectedK2 = findLinesWithPrefixesRemoved(testFile.content, "// RESULT_K2: ")
        assert(directives.size == expected.size) { "Sizes of test directives are different" }
        if (expectedK2.isNotEmpty()) {
            assert(expected.size == expectedK2.size) { "Sizes of test directives are different" }
        }

        val expectations =
            if (compileWithK2 && expectedK2.isNotEmpty()) {
                expectedK2
            } else {
                expected
            }
        return directives.zip(expectations).map { (text, result) -> CodeFragment(text, result, CodeFragmentKind.EXPRESSION) }
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

    private object AlwaysYesForEvaluator : KotlinSerializationEnabledChecker {
        override fun isEnabledFor(moduleDescriptor: ModuleDescriptor): Boolean {
            return true
        }
    }
}
