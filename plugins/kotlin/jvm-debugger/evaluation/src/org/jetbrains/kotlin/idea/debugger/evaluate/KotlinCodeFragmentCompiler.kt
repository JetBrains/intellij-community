// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.evaluate

import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.util.Range
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.compile.CodeFragmentCapturedValue
import org.jetbrains.kotlin.analysis.api.components.*
import org.jetbrains.kotlin.analysis.api.platform.restrictedAnalysis.KaRestrictedAnalysisException
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.idea.base.codeInsight.compiler.KotlinCompilerIdeAllowedErrorFilter
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.debugger.base.util.evaluate.ExecutionContext
import org.jetbrains.kotlin.idea.debugger.evaluate.KotlinEvaluator.Companion.logCompilation
import org.jetbrains.kotlin.idea.debugger.evaluate.classLoading.ClassToLoad
import org.jetbrains.kotlin.idea.debugger.evaluate.classLoading.GENERATED_CLASS_NAME
import org.jetbrains.kotlin.idea.debugger.evaluate.classLoading.GENERATED_FUNCTION_NAME
import org.jetbrains.kotlin.idea.debugger.evaluate.compilation.*
import org.jetbrains.kotlin.idea.debugger.stepping.smartStepInto.*
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.NameUtils
import org.jetbrains.kotlin.psi.KtCodeFragment
import org.jetbrains.kotlin.psi.KtOperationReferenceExpression
import java.util.concurrent.ExecutionException
import com.intellij.openapi.progress.runBlockingCancellable
import org.jetbrains.kotlin.idea.debugger.core.stackFrame.computeKotlinStackFrameInfos
import org.jetbrains.kotlin.idea.debugger.core.stepping.getLineRange
import kotlin.sequences.Sequence

interface KotlinCodeFragmentCompiler {
    val compilerType: CompilerType

    fun compileCodeFragment(context: ExecutionContext, codeFragment: KtCodeFragment): CompiledCodeFragmentData

    companion object {
        fun getInstance(): KotlinCodeFragmentCompiler = service<KotlinCodeFragmentCompiler>()
    }
}

class K2KotlinCodeFragmentCompiler : KotlinCodeFragmentCompiler {
    override val compilerType: CompilerType = CompilerType.K2

    @OptIn(KaExperimentalApi::class)
    override fun compileCodeFragment(
        context: ExecutionContext,
        codeFragment: KtCodeFragment
    ): CompiledCodeFragmentData {
        val stats = CodeFragmentCompilationStats()
        stats.origin = context.evaluationContext.origin
        fun onFinish(status: EvaluationCompilerResult) =
            KotlinDebuggerEvaluatorStatisticsCollector.logAnalysisAndCompilationResult(codeFragment.project, CompilerType.K2, status, stats)
        try {
            patchCodeFragment(context, codeFragment, stats)

            val result = stats.startAndMeasureAnalysisUnderReadAction {
                compiledCodeFragmentDataK2Impl(context, codeFragment)
            }.getOrThrow()
            onFinish(EvaluationCompilerResult.SUCCESS)
            return result
        } catch(e: ProcessCanceledException) {
            throw e
        } catch (e: Throwable) {
            val cause = unwrapEvaluationException(e)

            stats.compilerFailExceptionClass = extractExceptionCauseClass(cause)

            val isJustInvalidUserCode = cause is IncorrectCodeFragmentException
            onFinish(if (isJustInvalidUserCode) EvaluationCompilerResult.COMPILATION_FAILURE
                     else EvaluationCompilerResult.COMPILER_INTERNAL_ERROR)
            throw cause
        }
    }

    private fun unwrapEvaluationException(e: Throwable): Throwable {
        var current = e
        while (true) {
            val next = (current as? KaRestrictedAnalysisException)?.cause ?: (current as? ExecutionException)?.cause ?: return current
            current = next
        }
    }

    private class ExecutionStack(val context: ExecutionContext) : Sequence<PsiElement?> {
        override fun iterator(): Iterator<PsiElement?> = object : Iterator<PsiElement?> {
            private var rollbackFramesCount = 0
            private val frames by lazy { context.frameProxy.stackFrame.computeKotlinStackFrameInfos(alwaysComputeCallLocations = true) }

            override fun next(): PsiElement? {
                rollbackFramesCount++
                val currentLocation = frames[frames.size - rollbackFramesCount].callLocation ?: return null
                val executingFunctionDeclaration =
                    getCurrentDeclaration(context.debugProcess.positionManager, currentLocation) ?: return null
                val locationAtPreviousFrame = frames[frames.size - rollbackFramesCount - 1].callLocation ?: return null
                val sourcePositionAtPreviousFrame =
                    context.debugProcess.positionManager.getSourcePosition(locationAtPreviousFrame) ?: return null
                // NB: that is not completely correct when the line contains more than one calls, delimited by ';'.
                // For such cases, we will get an incomplete list of invocations, namely only the first one.
                // Due to the same reason, the smart-stepping does work at the moment for such cases.
                // That is why it's not safe not to filter out already executed invocations even if we found the only declaration-matching one.
                val previousFrameContainingExpr = sourcePositionAtPreviousFrame.getContainingExpression() ?: return null
                val previousFrameContainingExprRange = previousFrameContainingExpr.getLineRange() ?: return null

                // We collect all the calls in the containing expression and filter by declaration
                // If the only one matches, return it
                val callsInPreviousFrameContainingExpr = findSmartStepTargets(
                    previousFrameContainingExpr,
                    Range(previousFrameContainingExprRange.first, previousFrameContainingExprRange.last)
                )
                    .filterIsInstance<KotlinMethodSmartStepTarget>()
                    .filter { target ->
                        target.createMethodFilter().declarationMatches(executingFunctionDeclaration)
                    }
                if (callsInPreviousFrameContainingExpr.isEmpty()) return null

                // Filter out already executed invocations and return the first one that left.
                val filteringContext =
                    SmartStepIntoContext(
                        previousFrameContainingExpr,
                        context.debugProcess,
                        sourcePositionAtPreviousFrame,
                        previousFrameContainingExprRange.first..previousFrameContainingExprRange.last
                    )
                val notExecuted =
                    runBlockingCancellable {
                        callsInPreviousFrameContainingExpr.filterAlreadyExecuted(
                            filteringContext,
                            specificLocation = locationAtPreviousFrame
                        )
                    }
                if (notExecuted.isEmpty()) return null
                return notExecuted.first().highlightElement
            }

            override fun hasNext(): Boolean {
                return frames.size >= rollbackFramesCount
            }
        }
    }

    @OptIn(KaExperimentalApi::class)
    private fun compiledCodeFragmentDataK2Impl(context: ExecutionContext, codeFragment: KtCodeFragment): CompiledCodeFragmentData {
        val module = codeFragment.module

        val compilerConfiguration = CompilerConfiguration().apply {
            if (module != null) {
                put(CommonConfigurationKeys.MODULE_NAME, module.name)
            }
            put(CommonConfigurationKeys.LANGUAGE_VERSION_SETTINGS, codeFragment.languageVersionSettings)
            put(KaCompilerFacility.CODE_FRAGMENT_CLASS_NAME, GENERATED_CLASS_NAME)
            put(KaCompilerFacility.CODE_FRAGMENT_METHOD_NAME, GENERATED_FUNCTION_NAME)
        }

        return analyze(codeFragment) {
            try {
                val compilerTarget = KaCompilerTarget.Jvm(
                    isTestMode = false,
                    compiledClassHandler = null,
                    debuggerExtension = DebuggerExtension(ExecutionStack(context)))
                val allowedErrorFilter = KotlinCompilerIdeAllowedErrorFilter.getInstance()

                when (val result =
                    compile(codeFragment, compilerConfiguration, compilerTarget, allowedErrorFilter)) {
                    is KaCompilationResult.Success -> {
                        logCompilation(codeFragment)

                        val classes: List<ClassToLoad> = result.output
                            .filter { it.isClassFile && it.isCodeFragmentClassFile }
                            .map { ClassToLoad(it.internalClassName, it.path, it.content) }

                        val fragmentClass = classes.single { it.className == GENERATED_CLASS_NAME }
                        val methodSignature = getMethodSignature(fragmentClass)

                        val parameterInfo = computeCodeFragmentParameterInfo(result)
                        val ideCompilationResult = CompilationResult(classes, parameterInfo, mapOf(), methodSignature, CompilerType.K2)
                        createCompiledDataDescriptor(ideCompilationResult, result.canBeCached)
                    }
                    is KaCompilationResult.Failure -> {
                        val firstError = result.errors.first()
                        throw IncorrectCodeFragmentException(firstError.defaultMessage)
                    }
                }
            } catch (e: ProcessCanceledException) {
                throw e
            } catch (e: IndexNotReadyException) {
                throw e
            } catch (e: EvaluateException) {
                throw e
            } catch (e: Throwable) {
                reportErrorWithAttachments(context, codeFragment, e, headerMessage = "K2 compiler internal error")
                throw EvaluateExceptionUtil.createEvaluateException(e)
            }
        }
    }

    @KaExperimentalApi
    private fun computeCodeFragmentParameterInfo(result: KaCompilationResult.Success): K2CodeFragmentParameterInfo {
        val parameters = ArrayList<CodeFragmentParameter.Dumb>(result.capturedValues.size)
        val crossingBounds = HashSet<CodeFragmentParameter.Dumb>()

        for (capturedValue in result.capturedValues) {
            val parameter = capturedValue.toDumbCodeFragmentParameter() ?: continue
            parameters.add(parameter)

            if (capturedValue.isCrossingInlineBounds) {
                crossingBounds.add(parameter)
            }
        }

        return K2CodeFragmentParameterInfo(parameters, crossingBounds)
    }

    @OptIn(KaExperimentalApi::class)
    private fun CodeFragmentCapturedValue.toDumbCodeFragmentParameter(): CodeFragmentParameter.Dumb? {
        return when (this) {
            is CodeFragmentCapturedValue.Local ->
                CodeFragmentParameter.Dumb(CodeFragmentParameter.Kind.ORDINARY, name)
            is CodeFragmentCapturedValue.LocalDelegate ->
                CodeFragmentParameter.Dumb(CodeFragmentParameter.Kind.DELEGATED, displayText)
            is CodeFragmentCapturedValue.ContainingClass ->
                CodeFragmentParameter.Dumb(CodeFragmentParameter.Kind.DISPATCH_RECEIVER, "", displayText)
            is CodeFragmentCapturedValue.SuperClass ->
                CodeFragmentParameter.Dumb(CodeFragmentParameter.Kind.DISPATCH_RECEIVER, "", displayText)
            is CodeFragmentCapturedValue.ExtensionReceiver ->
                CodeFragmentParameter.Dumb(CodeFragmentParameter.Kind.EXTENSION_RECEIVER, name, displayText)
            is CodeFragmentCapturedValue.ContextReceiver -> {
                val name = NameUtils.contextReceiverName(index).asString()
                CodeFragmentParameter.Dumb(CodeFragmentParameter.Kind.CONTEXT_RECEIVER, name, displayText)
            }
            is CodeFragmentCapturedValue.ForeignValue -> {
                CodeFragmentParameter.Dumb(CodeFragmentParameter.Kind.FOREIGN_VALUE, name)
            }
            is CodeFragmentCapturedValue.BackingField ->
                CodeFragmentParameter.Dumb(CodeFragmentParameter.Kind.FIELD_VAR, name, displayText)
            is CodeFragmentCapturedValue.CoroutineContext ->
                CodeFragmentParameter.Dumb(CodeFragmentParameter.Kind.COROUTINE_CONTEXT, "")
            else -> null
        }
    }
}

fun isCodeFragmentClassPath(path: String): Boolean {
    return path == "$GENERATED_CLASS_NAME.class"
            || (path.startsWith("$GENERATED_CLASS_NAME\$") && path.endsWith(".class"))
}

@KaExperimentalApi
val KaCompiledFile.isCodeFragmentClassFile: Boolean
    get() = isCodeFragmentClassPath(path)

fun hasCastOperator(codeFragment: KtCodeFragment): Boolean {
    var result = false
    runReadAction {
        codeFragment.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (result) return
                result = element is KtOperationReferenceExpression && element.operationSignTokenType == KtTokens.AS_KEYWORD
                super.visitElement(element)
            }
        })
    }
    return result
}
