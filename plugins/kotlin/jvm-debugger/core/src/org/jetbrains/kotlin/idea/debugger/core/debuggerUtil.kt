// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:JvmName("DebuggerUtil")

package org.jetbrains.kotlin.idea.debugger.core

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.DebuggerManagerThreadImpl
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.events.DebuggerCommandImpl
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl
import com.intellij.debugger.impl.DebuggerContextImpl
import com.intellij.debugger.impl.DebuggerUtilsAsync
import com.intellij.debugger.jdi.MethodBytecodeUtil
import com.intellij.debugger.jdi.StackFrameProxyImpl
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiElement
import com.sun.jdi.*
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionSymbol
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.codegen.coroutines.INVOKE_SUSPEND_METHOD_NAME
import org.jetbrains.kotlin.codegen.inline.KOTLIN_STRATA_NAME
import org.jetbrains.kotlin.codegen.inline.isFakeLocalVariableForInline
import org.jetbrains.kotlin.codegen.topLevelClassAsmType
import org.jetbrains.kotlin.idea.base.psi.getLineEndOffset
import org.jetbrains.kotlin.idea.base.psi.getLineStartOffset
import org.jetbrains.kotlin.idea.base.psi.getTopmostElementAtOffset
import org.jetbrains.kotlin.idea.base.util.KOTLIN_FILE_EXTENSIONS
import org.jetbrains.kotlin.idea.codeinsight.utils.getFunctionSymbol
import org.jetbrains.kotlin.idea.debugger.base.util.*
import org.jetbrains.kotlin.idea.debugger.core.DebuggerUtils.getBorders
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallableReferenceExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.org.objectweb.asm.MethodVisitor
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.Type
import java.nio.file.Path
import java.util.*
import java.util.concurrent.CompletableFuture

fun Location.isInKotlinSources(): Boolean {
    return declaringType().isInKotlinSources()
}

fun ReferenceType.isInKotlinSources(): Boolean {
    val fileExtension = safeSourceName()?.substringAfterLast('.')?.lowercase(Locale.getDefault()) ?: ""
    return fileExtension in KOTLIN_FILE_EXTENSIONS || containsKotlinStrata()
}

fun ReferenceType.isInKotlinSourcesAsync(): CompletableFuture<Boolean> {
    return DebuggerUtilsAsync.sourceName(this)
        .thenApply {
            val fileExtension = it?.substringAfterLast('.')?.lowercase(Locale.getDefault()) ?: ""
            fileExtension in KOTLIN_FILE_EXTENSIONS
        }
        .exceptionally {
            if (DebuggerUtilsAsync.unwrap(it) is AbsentInformationException) {
                false
            }
            else {
                throw it
            }
        }
        .thenCombine(containsKotlinStrataAsync()) { kotlinExt, kotlinStrata -> kotlinExt || kotlinStrata }
}

fun ReferenceType.containsKotlinStrata() = availableStrata().contains(KOTLIN_STRATA_NAME)

fun ReferenceType.containsKotlinStrataAsync(): CompletableFuture<Boolean> =
    DebuggerUtilsAsync.availableStrata(this).thenApply { it.contains(KOTLIN_STRATA_NAME) }

fun isInsideInlineArgument(inlineArgument: KtExpression, location: Location, debugProcess: DebugProcessImpl): Boolean =
  isInlinedArgument(location.visibleVariables(debugProcess), inlineArgument)

/**
 * Check whether [inlineArgument] is a lambda that is inlined in bytecode
 * by looking for a marker inline variable corresponding to this lambda.
 *
 * For crossinline lambdas inlining depends on whether the lambda is passed further to a non-inline context.
 */
fun isInlinedArgument(inlineArgument: KtExpression, location: Location): Boolean =
  isInlinedArgument(location.method().safeVariables() ?: emptyList(), inlineArgument)

private fun isInlinedArgument(localVariables: List<LocalVariable>, inlineArgument: KtExpression): Boolean {
    if (inlineArgument !is KtFunction && inlineArgument !is KtCallableReferenceExpression) return false
    val markerLocalVariables = localVariables.filter { it.name().startsWith(JvmAbi.LOCAL_VARIABLE_NAME_PREFIX_INLINE_ARGUMENT) }

    return runReadAction {
        val lambdaOrdinal = (inlineArgument as? KtFunction)?.let { lambdaOrdinalByArgument(it) }
        val functionName = functionNameByArgument(inlineArgument) ?: "unknown"

        markerLocalVariables
            .map { it.name().drop(JvmAbi.LOCAL_VARIABLE_NAME_PREFIX_INLINE_ARGUMENT.length) }
            .any { variableName ->
                if (variableName.startsWith("-")) {
                    val lambdaClassName = ClassNameCalculator.getClassName(inlineArgument)?.substringAfterLast('.') ?: return@any false
                    dropInlineSuffix(variableName) == "-$functionName-$lambdaClassName"
                } else {
                    // For Kotlin up to 1.3.10
                    lambdaOrdinalByLocalVariable(variableName) == lambdaOrdinal
                            && functionNameByLocalVariable(variableName) == functionName
                }
            }
    }
}

fun <T : Any> DebugProcessImpl.invokeInManagerThread(f: (DebuggerContextImpl) -> T?): T? {
    var result: T? = null
    if (DebuggerManagerThreadImpl.isManagerThread()) {
        managerThread.invoke(object : DebuggerCommandImpl() {
            override fun action() {
                result = f(debuggerContext)
            }
        })
    }
    else {
        managerThread.invokeAndWait(object : DebuggerContextCommandImpl(debuggerContext) {
            override fun threadAction(suspendContext: SuspendContextImpl) {
                result = f(debuggerContext)
            }
        })
    }
    return result
}

private fun lambdaOrdinalByArgument(elementAt: KtFunction): Int {
    val className = ClassNameCalculator.getClassName(elementAt) ?: return 0
    return className.substringAfterLast("$").toIntOrNull() ?: 0
}

private fun functionNameByArgument(argument: KtExpression): String? =
    analyze(argument) {
        val function = getFunctionSymbol(argument) as? KtFunctionSymbol ?: return null
        return function.name.asString()
    }

private fun Location.visibleVariables(debugProcess: DebugProcessImpl): List<LocalVariable> {
    val stackFrame = MockStackFrame(this, debugProcess.virtualMachineProxy.virtualMachine)
    return stackFrame.visibleVariables()
}

// For Kotlin up to 1.3.10
private fun lambdaOrdinalByLocalVariable(name: String): Int = try {
    val nameWithoutPrefix = name.removePrefix(JvmAbi.LOCAL_VARIABLE_NAME_PREFIX_INLINE_ARGUMENT)
    Integer.parseInt(nameWithoutPrefix.substringBefore("$", nameWithoutPrefix))
} catch (e: NumberFormatException) {
    0
}

// For Kotlin up to 1.3.10
private fun functionNameByLocalVariable(name: String): String {
    val nameWithoutPrefix = name.removePrefix(JvmAbi.LOCAL_VARIABLE_NAME_PREFIX_INLINE_ARGUMENT)
    return nameWithoutPrefix.substringAfterLast("$", "unknown")
}

private class MockStackFrame(private val location: Location, private val vm: VirtualMachine) : StackFrame {
    private var visibleVariables: Map<String, LocalVariable>? = null

    override fun location() = location
    override fun thread() = null
    override fun thisObject() = null

    private fun createVisibleVariables() {
        if (visibleVariables == null) {
            val allVariables = location.method().safeVariables() ?: emptyList()
            val map = HashMap<String, LocalVariable>(allVariables.size)

            for (variable in allVariables) {
                if (variable.isVisible(this)) {
                    map[variable.name()] = variable
                }
            }
            visibleVariables = map
        }
    }

    override fun visibleVariables(): List<LocalVariable> {
        createVisibleVariables()
        val mapAsList = ArrayList(visibleVariables!!.values)
        mapAsList.sort()
        return mapAsList
    }

    override fun visibleVariableByName(name: String): LocalVariable? {
        createVisibleVariables()
        return visibleVariables!![name]
    }

    override fun getValue(variable: LocalVariable) = null
    override fun getValues(variables: List<LocalVariable>): Map<LocalVariable, Value> = emptyMap()
    override fun setValue(variable: LocalVariable, value: Value) {
    }

    override fun getArgumentValues(): List<Value> = emptyList()
    override fun virtualMachine() = vm
}

private const val INVOKE_SUSPEND_SIGNATURE = "(Ljava/lang/Object;)Ljava/lang/Object;"

fun StackFrameProxyImpl.isOnSuspensionPoint(): Boolean {
    val location = this.safeLocation() ?: return false

    if (isInSuspendMethod(location)) {
        val firstLocation = getFirstMethodLocation(location) ?: return false
        return firstLocation.safeLineNumber() == location.safeLineNumber() && firstLocation.codeIndex() != location.codeIndex()
    }

    return false
}

fun isInSuspendMethod(location: Location): Boolean {
    val method = location.method()
    val signature = method.signature()
    val continuationAsmType = continuationAsmType()
    return signature.contains(continuationAsmType.toString()) || isInvokeSuspendMethod(method)
}

fun isInvokeSuspendMethod(method: Method): Boolean {
    return method.name() == INVOKE_SUSPEND_METHOD_NAME && method.signature() == INVOKE_SUSPEND_SIGNATURE
}

private fun continuationAsmType() =
    StandardNames.COROUTINES_PACKAGE_FQ_NAME.child(Name.identifier("Continuation")).topLevelClassAsmType()

private fun getFirstMethodLocation(location: Location): Location? {
    val firstLocation = location.safeMethod()?.location() ?: return null
    if (firstLocation.safeLineNumber() < 0) {
        return null
    }

    return firstLocation
}

fun isOnSuspendReturnOrReenter(location: Location): Boolean {
    val firstLocation = getFirstMethodLocation(location) ?: return false
    val isAtFirstLine = firstLocation.safeLineNumber() == location.safeLineNumber()
    if (isAtFirstLine) {
        return doesMethodHaveSwitcher(location)
    }
    return false
}

private fun doesMethodHaveSwitcher(location: Location): Boolean {
    var result = false
    MethodBytecodeUtil.visit(location.method(), object : MethodVisitor(Opcodes.API_VERSION) {
        override fun visitFieldInsn(opcode: Int, owner: String, name: String, descriptor: String) {
            if (!result && name == "label" && descriptor == "I") {
                val className = Type.getObjectType(owner).className

                val methodClassName = location.method().declaringType().name()
                val methodName = location.method().name()

                if ((methodName == "invokeSuspend" && className == methodClassName) || // check in suspend lambda
                    className.startsWith("$methodClassName\$$methodName")) { // check in suspend method
                    result = true
                }
            }
        }
    }, false)
    return result
}

fun findElementAtLine(file: KtFile, line: Int): PsiElement? {
    val lineStartOffset = file.getLineStartOffset(line) ?: return null
    val lineEndOffset = file.getLineEndOffset(line) ?: return null

    return runReadAction {
        var topMostElement: PsiElement? = null
        var elementAt: PsiElement?
        for (offset in lineStartOffset until lineEndOffset) {
            elementAt = file.findElementAt(offset)
            if (elementAt != null) {
                topMostElement = getTopmostElementAtOffset(elementAt, offset)
                if (topMostElement is KtElement) {
                    break
                }
            }
        }

        topMostElement
    }
}

fun isKotlinFakeLineNumber(location: Location): Boolean {
    // The compiler inserts a fake line number for single-line inline function calls with a
    // lambda parameter such as:
    //
    //   42.let { it + 1 }
    //
    // This is done so that a breakpoint can be set on the lambda and on the line even though
    // the lambda is on the same line. When stepping, we do not want to stop at such fake line
    // numbers. They cause us to step to line 1 of the current file.
    try {
        if (location.lineNumber(KOTLIN_STRATA_NAME) == 1 &&
            location.sourceName(KOTLIN_STRATA_NAME) == "fake.kt" &&
            Path.of(location.sourcePath(KOTLIN_STRATA_NAME)) == Path.of("kotlin/jvm/internal/FakeKt")
        ) {
            return true
        }
    } catch (ignored: AbsentInformationException) {
    }
    return false
}

fun Method.getInlineFunctionAndArgumentVariablesToBordersMap(): Map<LocalVariable, ClosedRange<Location>> {
    return getInlineFunctionOrArgumentVariables()
        .mapNotNull {
            val borders = it.getBorders()
            if (borders == null)
                null
            else
                it to borders
        }
        .toMap()
}

fun Method.getInlineFunctionOrArgumentVariables(): Sequence<LocalVariable> {
    val localVariables = safeVariables() ?: return emptySequence()
    return localVariables
        .asSequence()
        .filter { isFakeLocalVariableForInline(it.name()) }
}

val DebugProcessImpl.canRunEvaluation: Boolean
    get() = suspendManager.pausedContext != null
