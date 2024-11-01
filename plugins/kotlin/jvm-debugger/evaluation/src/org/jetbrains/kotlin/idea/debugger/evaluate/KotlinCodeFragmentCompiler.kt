// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.evaluate

import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementVisitor
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.compile.CodeFragmentCapturedValue
import org.jetbrains.kotlin.analysis.api.components.*
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.config.JvmClosureGenerationScheme
import org.jetbrains.kotlin.idea.base.codeInsight.compiler.KotlinCompilerIdeAllowedErrorFilter
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.debugger.base.util.evaluate.ExecutionContext
import org.jetbrains.kotlin.idea.debugger.evaluate.KotlinEvaluator.Companion.logCompilation
import org.jetbrains.kotlin.idea.debugger.evaluate.classLoading.ClassToLoad
import org.jetbrains.kotlin.idea.debugger.evaluate.classLoading.GENERATED_CLASS_NAME
import org.jetbrains.kotlin.idea.debugger.evaluate.classLoading.GENERATED_FUNCTION_NAME
import org.jetbrains.kotlin.idea.debugger.evaluate.compilation.*
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.NameUtils
import org.jetbrains.kotlin.psi.KtCodeFragment
import org.jetbrains.kotlin.psi.KtOperationReferenceExpression
import java.util.concurrent.ExecutionException

interface KotlinCodeFragmentCompiler {
    fun compileCodeFragment(context: ExecutionContext, codeFragment: KtCodeFragment): CompiledCodeFragmentData

    companion object {
        fun getInstance(): KotlinCodeFragmentCompiler = service<KotlinCodeFragmentCompiler>()
    }
}

class K2KotlinCodeFragmentCompiler : KotlinCodeFragmentCompiler {
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
            val cause = (e as? ExecutionException)?.cause ?: e

            stats.compilerFailExceptionClass = extractExceptionCauseClass(e)

            val isJustInvalidUserCode = cause is IncorrectCodeFragmentException
            onFinish(if (isJustInvalidUserCode) EvaluationCompilerResult.COMPILATION_FAILURE
                     else EvaluationCompilerResult.COMPILER_INTERNAL_ERROR)
            throw e
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
            // Compile lambdas to anonymous classes, so that toString would show something sensible for them.
            put(JVMConfigurationKeys.LAMBDAS, JvmClosureGenerationScheme.CLASS)
        }

        return analyze(codeFragment) {
            try {
                val compilerTarget = KaCompilerTarget.Jvm(isTestMode = false)
                val allowedErrorFilter = KotlinCompilerIdeAllowedErrorFilter.getInstance()

                when (val result = compile(codeFragment, compilerConfiguration, compilerTarget, allowedErrorFilter)) {
                    is KaCompilationResult.Success -> {
                        logCompilation(codeFragment)

                        val classes: List<ClassToLoad> = result.output
                            .filter { it.isClassFile && it.isCodeFragmentClassFile }
                            .map { ClassToLoad(it.internalClassName, it.path, it.content) }

                        val fragmentClass = classes.single { it.className == GENERATED_CLASS_NAME }
                        val methodSignature = getMethodSignature(fragmentClass)

                        val parameterInfo = computeCodeFragmentParameterInfo(result)
                        val ideCompilationResult = CompilationResult(classes, parameterInfo, mapOf(), methodSignature, CompilerType.K2)
                        createCompiledDataDescriptor(ideCompilationResult)
                    }
                    is KaCompilationResult.Failure -> {
                        val firstError = result.errors.first()
                        throw IncorrectCodeFragmentException(firstError.defaultMessage)
                    }
                }
            } catch (e: ProcessCanceledException) {
                throw e
            } catch (e: EvaluateException) {
                throw e
            } catch (e: Throwable) {
                reportErrorWithAttachments(context, codeFragment, e)
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
