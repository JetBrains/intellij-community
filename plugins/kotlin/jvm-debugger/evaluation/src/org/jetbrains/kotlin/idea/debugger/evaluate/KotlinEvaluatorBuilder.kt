// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.evaluate

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.DebuggerUtils
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.evaluation.expression.*
import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.BitUtil
import com.sun.jdi.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.eval4j.*
import org.jetbrains.eval4j.jdi.JDIEval
import org.jetbrains.eval4j.jdi.asJdiValue
import org.jetbrains.eval4j.jdi.asValue
import org.jetbrains.eval4j.jdi.makeInitialFrame
import org.jetbrains.kotlin.idea.base.util.KotlinPlatformUtils
import org.jetbrains.kotlin.idea.base.util.caching.ConcurrentFactoryCache
import org.jetbrains.kotlin.idea.debugger.base.util.evaluate.ExecutionContext
import org.jetbrains.kotlin.idea.debugger.base.util.safeLocation
import org.jetbrains.kotlin.idea.debugger.base.util.safeMethod
import org.jetbrains.kotlin.idea.debugger.base.util.safeVisibleVariableByName
import org.jetbrains.kotlin.idea.debugger.coroutine.proxy.CoroutineStackFrameProxyImpl
import org.jetbrains.kotlin.idea.debugger.evaluate.classLoading.GENERATED_CLASS_NAME
import org.jetbrains.kotlin.idea.debugger.evaluate.classLoading.isEvaluationEntryPoint
import org.jetbrains.kotlin.idea.debugger.evaluate.compilation.*
import org.jetbrains.kotlin.idea.debugger.evaluate.compilingEvaluator.loadClassesSafely
import org.jetbrains.kotlin.idea.debugger.evaluate.variables.EvaluatorValueConverter
import org.jetbrains.kotlin.idea.debugger.evaluate.variables.VariableFinder
import org.jetbrains.kotlin.idea.util.application.attachmentByPsiFile
import org.jetbrains.kotlin.idea.util.application.isApplicationInternalMode
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.idea.util.application.merge
import org.jetbrains.kotlin.psi.KtCodeFragment
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.org.objectweb.asm.ClassReader
import org.jetbrains.org.objectweb.asm.Type
import org.jetbrains.org.objectweb.asm.tree.ClassNode
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import org.jetbrains.eval4j.Value as Eval4JValue

internal val LOG = Logger.getInstance(KotlinEvaluator::class.java)

object KotlinEvaluatorBuilder : EvaluatorBuilder {
    override fun build(codeFragment: PsiElement, position: SourcePosition?): ExpressionEvaluator {
        if (codeFragment !is KtCodeFragment) {
            return EvaluatorBuilderImpl.getInstance().build(codeFragment, position)
        }

        val context = codeFragment.context
        val file = context?.containingFile

        if (file != null && file !is KtFile) {
            reportError(codeFragment, position, "Unknown context${codeFragment.context?.javaClass}")
            evaluationException(KotlinDebuggerEvaluationBundle.message("error.bad.context"))
        }

        return ExpressionEvaluatorImpl(KotlinEvaluator(codeFragment, position))
    }
}

class KotlinEvaluator(val codeFragment: KtCodeFragment, private val sourcePosition: SourcePosition?) : Evaluator, ExternalExpressionEvaluator {

    override fun evaluate(context: EvaluationContextImpl): Any? {
        if (codeFragment.text.isEmpty()) {
            return context.suspendContext.virtualMachineProxy.mirrorOfVoid()
        }

        if (!context.debugProcess.isAttached) {
            throw EvaluateExceptionUtil.PROCESS_EXITED
        }

        val frameProxy = context.frameProxy ?: run {
            throw EvaluateExceptionUtil.NULL_STACK_FRAME
        }

        val operatingThread = context.suspendContext.thread ?: run {
            evaluationException(KotlinDebuggerEvaluationBundle.message("error.thread.unavailable"))
        }

        if (!operatingThread.isSuspended) {
            evaluationException(KotlinDebuggerEvaluationBundle.message("error.thread.not.suspended"))
        }

        try {
            val executionContext = ExecutionContext(context, frameProxy)
            return evaluateSafe(executionContext, codeFragment)
        } catch (e: CodeFragmentCodegenException) {
            evaluationException(e.reason)
        } catch (e: EvaluateException) {
            throw e
        } catch (e: IndexNotReadyException) {
            evaluationException(KotlinDebuggerEvaluationBundle.message("error.dumb.mode"))
        } catch (e: ProcessCanceledException) {
            evaluationException(e)
        } catch (e: Eval4JInterpretingException) {
            evaluationException(e.cause)
        } catch (e: Exception) {
            val isSpecialException = isSpecialException(e)
            if (isSpecialException) {
                evaluationException(e)
            }

            reportError(codeFragment, sourcePosition, e.message ?: KotlinDebuggerEvaluationBundle.message("error.exception.occurred"), e)

            val cause = if (e.message != null) ": ${e.message}" else ""
            evaluationException(KotlinDebuggerEvaluationBundle.message("error.cant.evaluate") + cause)
        }
    }

    private fun evaluateSafe(context: ExecutionContext, codeFragment: KtCodeFragment): Any? {
        val compiledData = getCompiledCodeFragment(context)

        return try {
            runEvaluation(context, compiledData).also {
                KotlinDebuggerEvaluatorStatisticsCollector.logEvaluationResult(codeFragment.project, StatisticsEvaluationResult.SUCCESS, compiledData.compilerType)
            }
        } catch (e: Throwable) {
            if (!isUnitTestMode()) {
                if (e !is EvaluateException && e !is Eval4JInterpretingException) {
                    KotlinDebuggerEvaluatorStatisticsCollector.logEvaluationResult(
                        codeFragment.project,
                        StatisticsEvaluationResult.FAILURE,
                        compiledData.compilerType
                    )
                }
                if (isApplicationInternalMode()) {
                    reportErrorWithAttachments(context, codeFragment, e,
                                               prepareBytecodes(compiledData),
                                               "Can't perform evaluation. Compiled by ${compiledData.compilerType} compiler")
                }
            }
            throw e
        }
    }

    private fun prepareBytecodes(compiledData: CompiledCodeFragmentData): List<Pair<String, String>> {
        // TODO run javap, if found valid java home?
        val result = buildString {
            for ((className, relativeFileName, bytes) in compiledData.classes) {
                appendLine("Bytecode for $className in $relativeFileName:")
                appendLine(bytes.joinToString())
                appendLine()
            }
        }
        return listOf("bytecodes.txt" to result)
    }

    private fun runEvaluation(
        context: ExecutionContext,
        compiledData: CompiledCodeFragmentData
    ): Value? {
        val classLoadingResult = loadClassesSafely(context, compiledData.classes)
        val classLoaderRef = classLoadingResult.getOrNull()

        val result = if (classLoaderRef != null) {
            try {
                return evaluateWithCompilation(context, compiledData, classLoaderRef)
            } catch (e: Throwable) {
                LOG.warn("Compiling evaluator failed: " + e.message, e)
                evaluateWithEval4J(context, compiledData, classLoaderRef)
            }
        } else {
            evaluateWithEval4J(context, compiledData, context.classLoader)
        }

        return result.toJdiValue(context)
    }

    private fun getCompiledCodeFragment(context: ExecutionContext): CompiledCodeFragmentData {
        val cache = runReadAction {
            val contextElement = codeFragment.context ?: return@runReadAction null
            CachedValuesManager.getCachedValue(contextElement, OnRefreshCachedValueProvider(context.project))
        }
        if (cache == null) return compileCodeFragment(context)

        val key = buildString {
            appendLine(codeFragment.importsToString())
            append(codeFragment.text)
        }

        return cache.get(key) {
            compileCodeFragment(context)
        }
    }

    private class OnRefreshCachedValueProvider(private val project: Project) : CachedValueProvider<ConcurrentFactoryCache<String, CompiledCodeFragmentData>> {
        override fun compute(): CachedValueProvider.Result<ConcurrentFactoryCache<String, CompiledCodeFragmentData>> {
            val storage = ConcurrentHashMap<String, CompiledCodeFragmentData>()
            return CachedValueProvider.Result(ConcurrentFactoryCache(storage), KotlinDebuggerSessionRefreshTracker.getInstance(project))
        }
    }

    private fun compileCodeFragment(context: ExecutionContext): CompiledCodeFragmentData {
        try {
            return KotlinCodeFragmentCompiler.getInstance().compileCodeFragment(context, codeFragment)
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        }
    }

    private fun evaluateWithCompilation(
        context: ExecutionContext,
        compiledData: CompiledCodeFragmentData,
        classLoader: ClassLoaderReference
    ): Value? {
        return runEvaluation(context, compiledData, classLoader) { args ->
            val mainClassType = context.findClass(GENERATED_CLASS_NAME, classLoader) as? ClassType
                ?: error("Can not find class \"$GENERATED_CLASS_NAME\"")
            val mainMethod = mainClassType.methods().single { isEvaluationEntryPoint(it.name()) }
            val returnValue = context.invokeMethod(mainClassType, mainMethod, args)
            EvaluatorValueConverter.unref(returnValue)
        }
    }

    private fun evaluateWithEval4J(
        context: ExecutionContext,
        compiledData: CompiledCodeFragmentData,
        classLoader: ClassLoaderReference?
    ): InterpreterResult {
        val mayRetry = context.evaluationContext.isMayRetryEvaluation
        val mainClassBytecode = compiledData.mainClass.bytes
        val mainClassAsmNode = ClassNode().apply { ClassReader(mainClassBytecode).accept(this, 0) }
        val mainMethod = mainClassAsmNode.methods.first { it.isEvaluationEntryPoint }

        return runEvaluation(context, compiledData, classLoader ?: context.evaluationContext.classLoader) { args ->
            val vm = context.vm.virtualMachine
            val thread = context.suspendContext.thread?.threadReference?.takeIf { it.isSuspended }
                ?: error("Can not find a thread to run evaluation on")

            val eval = object : JDIEval(vm, classLoader, thread, context.invokePolicy) {
                override fun jdiInvokeStaticMethod(type: ClassType, method: Method, args: List<Value?>, invokePolicy: Int): Value? {
                    return context.invokeMethod(type, method, args)
                }

                override fun jdiInvokeStaticMethod(type: InterfaceType, method: Method, args: List<Value?>, invokePolicy: Int): Value? {
                    return context.invokeMethod(type, method, args)
                }

                override fun jdiInvokeMethod(obj: ObjectReference, method: Method, args: List<Value?>, policy: Int): Value? {
                    var invocationOptions = 0
                    if (BitUtil.isSet(policy, ObjectReference.INVOKE_NONVIRTUAL)) {
                        invocationOptions = ObjectReference.INVOKE_NONVIRTUAL
                    }
                    return context.invokeMethod(obj, method, args, invocationOptions)
                }

                override fun jdiNewInstance(clazz: ClassType, ctor: Method, args: List<Value?>, policy: Int): Value {
                    return context.newInstance(clazz, ctor, args)
                }

                override fun jdiMirrorOfString(str: String): StringReference {
                    return DebuggerUtilsEx.mirrorOfString(str, context.evaluationContext)
                }

                override fun jdiNewArray(arrayType: ArrayType, size: Int): ArrayReference {
                    return DebuggerUtilsEx.mirrorOfArray(arrayType, size, context.evaluationContext)
                }

                override fun shouldInvokeMethodWithReflection(method: Method, args: List<Value?>): Boolean {
                    // invokeMethod in ExecutionContext already handles everything
                    return false
                }

                override fun loadType(classType: Type, classLoader: ClassLoaderReference?): ReferenceType {
                    return context.debugProcess.findClass(context.evaluationContext, classType.className, classLoader)
                }
            }

            fun interpreterLoop() = interpreterLoop(mainMethod, makeInitialFrame(mainMethod, args.map { it.asValue() }), eval)
            fun keepResult(result: InterpreterResult) {
                val jdiObject = when (result) {
                    is ValueReturned -> result.result
                    is ExceptionThrown -> result.exception
                    else -> return
                }.obj() as? ObjectReference? ?: return
                context.evaluationContext.keep(jdiObject)
            }
            if (mayRetry) {
                DebuggerUtils.getInstance().processCollectibleValue(object : ThrowableComputable<InterpreterResult, EvaluateException> {
                    override fun compute() = interpreterLoop()
                }, { keepResult(it); it }, context.evaluationContext)
            } else {
                interpreterLoop()
            }
        }
    }

    private fun <T> runEvaluation(
        context: ExecutionContext,
        compiledData: CompiledCodeFragmentData,
        classLoader: ClassLoaderReference?,
        block: (List<Value?>) -> T
    ): T {
        // Preload additional classes
        compiledData.classes
            .filter { !it.isMainClass }
            .forEach { context.findClass(it.className, classLoader) }

        for (parameterType in compiledData.mainMethodSignature.parameterTypes) {
            context.findClass(parameterType, classLoader)
        }

        val variableFinder = VariableFinder(context)
        val args = calculateMainMethodCallArguments(variableFinder, compiledData)

        val result = block(args)

        for (wrapper in variableFinder.refWrappers) {
            updateLocalVariableValue(variableFinder.evaluatorValueConverter, wrapper)
        }

        return result
    }

    private fun updateLocalVariableValue(converter: EvaluatorValueConverter, ref: VariableFinder.RefWrapper) {
        val frameProxy = converter.context.frameProxy
        val newValue = EvaluatorValueConverter.unref(ref.wrapper)
        val variable = frameProxy.safeVisibleVariableByName(ref.localVariableName)
        if (variable != null) {
            try {
                frameProxy.setValue(variable, newValue)
            } catch (e: InvalidTypeException) {
                LOG.error("Cannot update local variable value: expected type ${variable.type}, actual type ${newValue?.type()}", e)
            }
        } else if (frameProxy is CoroutineStackFrameProxyImpl) {
            frameProxy.updateSpilledVariableValue(ref.localVariableName, newValue)
        }
    }

    private fun calculateMainMethodCallArguments(variableFinder: VariableFinder, compiledData: CompiledCodeFragmentData): List<Value?> {
        val asmValueParameters = compiledData.mainMethodSignature.parameterTypes
        val valueParameters = compiledData.parameters
        require(asmValueParameters.size == valueParameters.size)

        val args = valueParameters.zip(asmValueParameters)

        return args.map { (parameter, asmType) ->
            val result = variableFinder.find(parameter, asmType, codeFragment)

            if (result == null) {
                val name = parameter.debugString
                val frameProxy = variableFinder.context.frameProxy

                fun isInsideDefaultInterfaceMethod(): Boolean {
                    val method = frameProxy.safeLocation()?.safeMethod() ?: return false
                    val desc = method.signature()
                    return method.name().endsWith("\$default") && DEFAULT_METHOD_MARKERS.any { desc.contains("IL$it;)") }
                }

                if (parameter.kind == CodeFragmentParameter.Kind.COROUTINE_CONTEXT) {
                    evaluationException(KotlinDebuggerEvaluationBundle.message("error.coroutine.context.unavailable"))
                } else if (parameter in compiledData.crossingBounds) {
                    evaluationException(KotlinDebuggerEvaluationBundle.message("error.not.captured", name))
                } else if (parameter.kind == CodeFragmentParameter.Kind.FIELD_VAR) {
                    evaluationException(KotlinDebuggerEvaluationBundle.message("error.cant.find.backing.field", parameter.name))
                } else if (parameter.kind == CodeFragmentParameter.Kind.ORDINARY && isInsideDefaultInterfaceMethod()) {
                    evaluationException(KotlinDebuggerEvaluationBundle.message("error.parameter.evaluation.default.methods"))
                } else if (parameter.kind == CodeFragmentParameter.Kind.ORDINARY && frameProxy is CoroutineStackFrameProxyImpl) {
                    evaluationException(KotlinDebuggerEvaluationBundle.message("error.variable.was.optimised"))
                } else {
                    evaluationException(KotlinDebuggerEvaluationBundle.message("error.cant.find.variable", name, asmType.className))
                }
            }

            result.value
        }
    }

    override fun getModifier() = null

    companion object {
        @get:TestOnly
        @get:ApiStatus.Internal
        var LOG_COMPILATIONS: Boolean = false

        fun logCompilation(codeFragment: KtCodeFragment) {
            val needLog = @Suppress("TestOnlyProblems") LOG_COMPILATIONS &&
                    true != codeFragment.getUserData(KotlinPlatformUtils.suppressCodeFragmentCompilationLogging)
            if (needLog) {
                LOG.debug("Compile bytecode for ${codeFragment.text}")
            }
        }

        private val DEFAULT_METHOD_MARKERS = listOf("java/lang/Object", "kotlin/jvm/internal/DefaultConstructorMarker")

        private fun InterpreterResult.toJdiValue(context: ExecutionContext): Value? {
            val jdiValue = when (this) {
                is ValueReturned -> result
                is ExceptionThrown -> {
                    when (kind) {
                        ExceptionThrown.ExceptionKind.FROM_EVALUATED_CODE -> {
                            val exceptionReference = exception.value as ObjectReference
                            evaluationException(InvocationException(exceptionReference))
                        }
                        ExceptionThrown.ExceptionKind.BROKEN_CODE -> throw exception.value as Throwable
                        else -> evaluationException(exception.toString())
                    }
                }
                is AbnormalTermination -> evaluationException(message)
                else -> throw IllegalStateException("Unknown result value produced by eval4j")
            }

            val sharedVar = if ((jdiValue is AbstractValue<*>)) getValueIfSharedVar(jdiValue) else null
            return sharedVar?.value ?: jdiValue.asJdiValue(context.vm.virtualMachine) { jdiValue.asmType }
        }

        private fun getValueIfSharedVar(value: Eval4JValue): VariableFinder.Result? {
            val obj = value.obj { value.asmType } as? ObjectReference ?: return null
            return VariableFinder.Result(EvaluatorValueConverter.unref(obj))
        }
    }
}

private fun isSpecialException(th: Throwable): Boolean {
    return when (th) {
        is ClassNotPreparedException,
        is InternalException,
        is AbsentInformationException,
        is ClassNotLoadedException,
        is IncompatibleThreadStateException,
        is InconsistentDebugInfoException,
        is ObjectCollectedException,
        is VMDisconnectedException -> true
        else -> false
    }
}

private fun reportError(codeFragment: KtCodeFragment, position: SourcePosition?, message: String, throwable: Throwable? = null) {
    runReadAction {
        val contextFile = codeFragment.context?.containingFile

        val attachments = listOfNotNull(
            attachmentByPsiFile(contextFile),
            attachmentByPsiFile(codeFragment),
            Attachment("breakpoint.info", "Position: " + position?.run { "${file.name}:$line" }),
            Attachment("context.info", runReadAction { codeFragment.context?.text ?: "null" })
        )

        val decapitalizedMessage = message.replaceFirstChar { it.lowercase(Locale.getDefault()) }
        LOG.error(
            "Cannot evaluate a code fragment of type ${codeFragment::class.java}: $decapitalizedMessage",
            throwable,
            attachments.merge()
        )
    }
}

fun createCompiledDataDescriptor(result: CompilationResult): CompiledCodeFragmentData {
    val localFunctionSuffixes = result.localFunctionSuffixes

    val dumbParameters = ArrayList<CodeFragmentParameter.Dumb>(result.parameterInfo.parameters.size)
    for (dumb in result.parameterInfo.parameters) {
        if (dumb.kind == CodeFragmentParameter.Kind.LOCAL_FUNCTION) {
            val suffix = localFunctionSuffixes[dumb]
            if (suffix != null) {
                dumbParameters += dumb.copy(name = dumb.name + suffix)
                continue
            }
        }

        dumbParameters += dumb
    }

    return CompiledCodeFragmentData(
        result.classes,
        dumbParameters,
        result.parameterInfo.crossingBounds,
        result.mainMethodSignature,
        result.compilerType
    )
}

fun evaluationException(msg: String): Nothing = throw EvaluateExceptionUtil.createEvaluateException(msg)
fun evaluationException(e: Throwable): Nothing = throw EvaluateExceptionUtil.createEvaluateException(e)

enum class CompilerType {
    OLD, IR, K2
}
