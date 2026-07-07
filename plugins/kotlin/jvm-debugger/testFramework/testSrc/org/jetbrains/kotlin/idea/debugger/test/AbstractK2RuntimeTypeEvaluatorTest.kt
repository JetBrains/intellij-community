// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.test

import com.intellij.debugger.engine.ContextUtil
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.evaluation.CodeFragmentKind
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl
import com.intellij.debugger.engine.evaluation.expression.EvaluatorBuilderImpl
import com.intellij.debugger.engine.evaluation.expression.ExpressionEvaluator
import com.intellij.debugger.impl.DebuggerContextImpl
import com.intellij.debugger.impl.DebuggerContextImpl.createDebuggerContext
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaImplementationDetail
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypePointer
import org.jetbrains.kotlin.config.JvmClosureGenerationScheme
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.debugger.core.CodeFragmentContextTuner
import org.jetbrains.kotlin.idea.debugger.evaluate.DebugContextProvider
import org.jetbrains.kotlin.idea.debugger.evaluate.KotlinK2RuntimeTypeEvaluator
import org.jetbrains.kotlin.idea.debugger.test.preference.DebuggerPreferences
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.types.Variance
import java.util.Collections

/**
 * Verifies [KotlinK2RuntimeTypeEvaluator.getCastableRuntimeType]: at a `//Breakpoint!` we evaluate each `// EXPRESSION:`
 * directive to a runtime value, compute its castable runtime type with the K2 runtime type evaluator, and compare the
 * rendered type against the corresponding `// RUNTIME_TYPE:` directive.
 */
abstract class AbstractK2RuntimeTypeEvaluatorTest : KotlinDescriptorTestCaseWithStepping() {
    override val compileWithK2: Boolean get() = true

    override fun lambdasGenerationScheme(): JvmClosureGenerationScheme = JvmClosureGenerationScheme.INDY

    // The test asserts the computed runtime types directly, so no golden `*.out` file is needed.
    override fun checkTestOutput() {}

    // Collected on the debugger manager thread (in the breakpoint action) and read in tearDown via throwExceptionsIfAny().
    private val failures = Collections.synchronizedList(mutableListOf<String>())

    override fun doMultiFileTest(files: TestFiles, preferences: DebuggerPreferences) {
        val content = files.wholeFile.content
        val expressions = InTextDirectivesUtils.findLinesWithPrefixesRemoved(content, "// EXPRESSION: ")
        val expectedTypes = InTextDirectivesUtils.findLinesWithPrefixesRemoved(content, "// RUNTIME_TYPE: ")
        assertEquals(
            "Each '// EXPRESSION:' directive must be paired with a '// RUNTIME_TYPE:' directive",
            expressions.size,
            expectedTypes.size,
        )
        assertTrue("At least one '// EXPRESSION:' directive is required", expressions.isNotEmpty())

        doOnBreakpoint {
            try {
                checkRuntimeTypes(this, expressions, expectedTypes)
            } finally {
                resume(this)
            }
        }
        finish()
    }

    override fun throwExceptionsIfAny() {
        super.throwExceptionsIfAny()
        val collected = failures.toList()
        if (collected.isNotEmpty()) {
            throw AssertionError(collected.joinToString("\n"))
        }
    }

    private fun checkRuntimeTypes(suspendContext: SuspendContextImpl, expressions: List<String>, expectedTypes: List<String>) {
        val sourcePosition = ContextUtil.getSourcePosition(suspendContext)
        val frameProxy = getFrameProxy(suspendContext)
        val threadProxy = frameProxy?.threadProxy()
        val debuggerContext = createDebuggerContext(myDebuggerSession, suspendContext, threadProxy, frameProxy)
        debuggerContext.initCaches()

        val contextElement = ReadAction.nonBlocking<PsiElement?> {
            CodeFragmentContextTuner.getInstance().tuneContextElement(ContextUtil.getContextElement(debuggerContext))
        }.executeSynchronously() ?: error("Cannot find a context element for the breakpoint")

        DebugContextProvider.supplyTestDebugContext(contextElement, debuggerContext)

        suspendContext.runActionInSuspendCommand {
            for ((expressionText, expectedType) in expressions.zip(expectedTypes)) {
                val actualType = computeRuntimeType(expressionText, contextElement, sourcePosition, debuggerContext)
                if (actualType != expectedType) {
                    failures += "Runtime type of `$expressionText`: expected `$expectedType`, but was `$actualType`"
                }
            }
        }
    }

    @OptIn(KaExperimentalApi::class, KaImplementationDetail::class)
    private fun computeRuntimeType(
        expressionText: String,
        contextElement: PsiElement,
        sourcePosition: com.intellij.debugger.SourcePosition?,
        debuggerContext: DebuggerContextImpl,
    ): String? {
        // Evaluate the expression to a runtime value, exactly as the debugger does before computing a castable type.
        val item = TextWithImportsImpl(CodeFragmentKind.EXPRESSION, expressionText, "", KotlinFileType.INSTANCE)
        val jdiEvaluator = ReadAction.nonBlocking<ExpressionEvaluator> {
            EvaluatorBuilderImpl.build(item, contextElement, sourcePosition, project)
        }.executeSynchronously()
        val value = jdiEvaluator.evaluate(evaluationContext) ?: return null

        // Compute runtime type
        val expression = ReadAction.nonBlocking<KtExpression?> {
            KtPsiFactory(project).createExpressionCodeFragment(expressionText, contextElement).getContentElement()
        }.executeSynchronously() ?: error("Cannot create a code fragment for expression `$expressionText`")

        val typeEvaluator = object : KotlinK2RuntimeTypeEvaluator(null, expression, debuggerContext, EmptyProgressIndicator()) {
            override fun typeCalculationFinished(type: KaTypePointer<KaType>?) {}
            override fun commandCancelled() {}

            fun computeType(): KaTypePointer<KaType>? = getCastableRuntimeType(evaluationContext.debugProcess.searchScope, value)
        }

        val pointer = typeEvaluator.computeType() ?: return null
        return ReadAction.nonBlocking<String?> {
            analyze(expression) {
                pointer.restore(this)?.render(KaTypeRendererForSource.WITH_QUALIFIED_NAMES, position = Variance.INVARIANT)
            }
        }.executeSynchronously()
    }
}
