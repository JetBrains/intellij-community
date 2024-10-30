// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:JvmName("DebuggerUtil")

package org.jetbrains.kotlin.idea.debugger.core

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.DebuggerManagerThreadImpl
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl
import com.intellij.debugger.impl.DebuggerContextImpl
import com.intellij.debugger.impl.DebuggerUtilsAsync
import com.intellij.debugger.jdi.MethodBytecodeUtil
import com.intellij.debugger.jdi.StackFrameProxyImpl
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiElement
import com.sun.jdi.*
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.codegen.inline.dropInlineScopeInfo
import org.jetbrains.kotlin.idea.base.psi.getLineEndOffset
import org.jetbrains.kotlin.idea.base.psi.getLineStartOffset
import org.jetbrains.kotlin.idea.base.psi.getTopmostElementAtOffset
import org.jetbrains.kotlin.idea.base.util.KOTLIN_FILE_EXTENSIONS
import org.jetbrains.kotlin.idea.codeinsight.utils.getFunctionSymbol
import org.jetbrains.kotlin.idea.debugger.base.util.*
import org.jetbrains.kotlin.idea.debugger.base.util.KotlinDebuggerConstants.INVOKE_SUSPEND_METHOD_NAME
import org.jetbrains.kotlin.idea.debugger.base.util.KotlinDebuggerConstants.KOTLIN_STRATA_NAME
import org.jetbrains.kotlin.idea.debugger.core.DebuggerUtils.getBorders
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.psi.*
import org.jetbrains.org.objectweb.asm.Label
import org.jetbrains.org.objectweb.asm.MethodVisitor
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.Type
import org.jetbrains.org.objectweb.asm.tree.MethodNode
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

internal suspend fun isInsideInlineArgument(inlineArgument: KtExpression, location: Location): Boolean =
    isInlinedArgument(location.visibleVariables(location.virtualMachine()), inlineArgument)

/**
 * Check whether [inlineArgument] is a lambda that is inlined in bytecode
 * by looking for a marker inline variable corresponding to this lambda.
 *
 * For crossinline lambdas inlining depends on whether the lambda is passed further to a non-inline context.
 */
internal suspend fun isInlinedArgument(inlineArgument: KtExpression, location: Location): Boolean =
    isInlinedArgument(location.method().safeVariables() ?: emptyList(), inlineArgument)

private suspend fun isInlinedArgument(localVariables: List<LocalVariable>, inlineArgument: KtExpression): Boolean {
    if (inlineArgument !is KtFunction && inlineArgument !is KtCallableReferenceExpression) return false
    val markerLocalVariables = localVariables
        .map { it.name() }
        .filter { it.startsWith(JvmAbi.LOCAL_VARIABLE_NAME_PREFIX_INLINE_ARGUMENT) }
    if (markerLocalVariables.isEmpty()) return false

    return readAction {
        val lambdaOrdinal = (inlineArgument as? KtFunction)?.let { lambdaOrdinalByArgument(it) }
        val functionName = functionNameByArgument(inlineArgument) ?: "unknown"

        markerLocalVariables
            .map { it.drop(JvmAbi.LOCAL_VARIABLE_NAME_PREFIX_INLINE_ARGUMENT.length) }
            .any { variableName ->
                if (variableName.startsWith("-")) {
                    val lambdaClassName = ClassNameCalculator.getClassName(inlineArgument)?.substringAfterLast('.') ?: return@any false
                    val cleanedVarName = dropInlineSuffix(variableName).dropInlineScopeInfo().removePrefix("-")
                    if (!cleanedVarName.endsWith("-$lambdaClassName")) return@any false
                    val candidateMethodName = cleanedVarName.removeSuffix("-$lambdaClassName")
                    candidateMethodName == functionName || nameMatchesUpToDollar(candidateMethodName, functionName)
                } else {
                    // For Kotlin up to 1.3.10
                    lambdaOrdinalByLocalVariable(variableName) == lambdaOrdinal
                            && functionNameByLocalVariable(variableName) == functionName
                }
            }
    }
}

// Internal functions have a '$<MODULE_NAME>' suffix
// Local functions can be '$1' suffixed
internal fun nameMatchesUpToDollar(methodName: String, targetMethodName: String): Boolean {
    return methodName.startsWith("$targetMethodName\$")
}

fun <T : Any> DebugProcessImpl.invokeInManagerThread(f: (DebuggerContextImpl) -> T?): T? {
    if (DebuggerManagerThreadImpl.isManagerThread()) {
        return f(debuggerContext)
    }
    var result: T? = null
    managerThread.invokeAndWait(object : DebuggerContextCommandImpl(debuggerContext) {
        override fun threadAction(suspendContext: SuspendContextImpl) {
            result = f(debuggerContext)
        }
    })
    return result
}

private fun lambdaOrdinalByArgument(elementAt: KtFunction): Int {
    val className = ClassNameCalculator.getClassName(elementAt) ?: return 0
    return className.substringAfterLast("$").toIntOrNull() ?: 0
}

private fun functionNameByArgument(argument: KtExpression): String? =
    runDumbAnalyze(argument, fallback = null) f@{
        val function = getFunctionSymbol(argument) as? KaNamedFunctionSymbol ?: return@f null
        function.name.asString()
    }

private fun Location.visibleVariables(virtualMachine: VirtualMachine): List<LocalVariable> {
    val stackFrame = MockStackFrame(this, virtualMachine)
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
        return firstLocation.codeIndex() != location.codeIndex()
                && isOnSuspendReturnOrReenter(location)
    }

    return false
}

fun isInSuspendMethod(location: Location): Boolean {
    val method = location.method()
    val signature = method.signature()
    return signature.contains(CONTINUATION_TYPE.toString()) || isInvokeSuspendMethod(method)
}

fun isInvokeSuspendMethod(method: Method): Boolean {
    return method.name() == INVOKE_SUSPEND_METHOD_NAME && method.signature() == INVOKE_SUSPEND_SIGNATURE
}

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
    if (DexDebugFacility.isDex(location.virtualMachine())) {
        return false
    }

    var result = false
    MethodBytecodeUtil.visit(location.method(), object : MethodVisitor(Opcodes.API_VERSION) {
        override fun visitFieldInsn(opcode: Int, owner: String, name: String, descriptor: String) {
            if (!result && checkContinuationLabelField(location, name, descriptor, owner)) {
                result = true
            }
        }
    }, false)
    return result
}

private fun checkContinuationLabelField(location: Location, name: String?, descriptor: String?, owner: String?): Boolean {
    if (name == null || descriptor == null || owner == null) return false
    if (name == "label" && descriptor == "I") {
        val className = Type.getObjectType(owner).className

        val methodClassName = location.method().declaringType().name()

        if (isInSuspendMethod(location) && className.startsWith(methodClassName))
            return true
    }
    return false
}

private class CoroutineStateMachineVisitor(method: Method, private val resumeLocation: Location)
    : MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC, method.name(), "", "(Ljava/lang/Object;)Ljava/lang/Object;", emptyArray()),
      MethodBytecodeUtil.InstructionOffsetReader {
    /*
        This visitor visits the state machine of the suspend function and looks for the ARETURN instruction that follows
      the current suspending call (firstReturnAfterSuspensionOffset), and for the next instruction that will be executed after
      the current suspending call (nextCallOffset).
        The visitor relies on the following pattern:

      #resumeLocationOffset
      ... function arguments ...
      PUTFIELD MyClass$main$1$1.label : I
      INVOKESTATIC MyClass$foo (IILkotlin/coroutines/Continuation;)Ljava/lang/Object;
      DUP
      ALOAD 3
      IF_ACMPNE L8 // this is the label
      L9
      LINENUMBER 8 L9
      ALOAD 3
      ARETURN

      Note: This is a WA for Kotlin compiler versions that do not provide the resumeLocaiton API with coroutines DebugMetadata (KT-67555)
     */
    private var myState: CurrentInsn = CurrentInsn.NONE
    private enum class CurrentInsn {
        NONE,
        SUSPEND_METHOD_ARGS, // corresponds to all instructions that put function arguments on stack
        PUTFIELD_CONTINUATION_LABEL,
        INVOKE_SUSPEND_METHOD,
        DUP,
        ALOAD_SUSPEND_RESULT_BEFORE_COMPARE,
        IF_ACMPNE,
        ALOAD_SUSPEND_RESULT_FOR_SUSPEND_RETURN
    }
    private var coroutineSuspendedState = CoroutineSuspendedState.NONE
    private enum class CoroutineSuspendedState {
        NONE,
        INVOKE_GET_COROUTINE_SUSPENDED
    }

    private var coroutineSuspendedLocalVarIndex = -1
    private var currentByteCodeOffSet = -1
    private var nextCallLabel: Label? = null
    var firstReturnAfterSuspensionOffset = -1
    var nextCallOffset = -1

    override fun readBytecodeInstructionOffset(offset: Int) {
        currentByteCodeOffSet = offset
    }

    override fun visitMethodInsn(opcodeAndSource: Int, owner: String?, name: String?, descriptor: String?, isInterface: Boolean) {
        super.visitMethodInsn(opcodeAndSource, owner, name, descriptor, isInterface)
        when {
            coroutineSuspendedState == CoroutineSuspendedState.NONE && isGetCoroutineSuspended(name, owner) -> coroutineSuspendedState = CoroutineSuspendedState.INVOKE_GET_COROUTINE_SUSPENDED
            myState == CurrentInsn.NONE && reachedResumedLocation() -> myState = CurrentInsn.SUSPEND_METHOD_ARGS
            myState == CurrentInsn.SUSPEND_METHOD_ARGS && !isSuspendFunction(name, descriptor) -> {} // skip function arguments
            myState == CurrentInsn.PUTFIELD_CONTINUATION_LABEL && isSuspendFunction(name, descriptor) -> myState = CurrentInsn.INVOKE_SUSPEND_METHOD
            else -> {
                myState = CurrentInsn.NONE
                coroutineSuspendedState = CoroutineSuspendedState.NONE
            }
        }
    }

    override fun visitInsn(opcode: Int) {
        super.visitInsn(opcode)
        when {
            // reached resumed location or skipping arguments
            myState == CurrentInsn.NONE && reachedResumedLocation() -> myState = CurrentInsn.SUSPEND_METHOD_ARGS
            myState == CurrentInsn.SUSPEND_METHOD_ARGS -> {} // skip function arguments
            myState == CurrentInsn.INVOKE_SUSPEND_METHOD && opcode == Opcodes.DUP -> myState = CurrentInsn.DUP
            myState == CurrentInsn.ALOAD_SUSPEND_RESULT_FOR_SUSPEND_RETURN && opcode == Opcodes.ARETURN -> firstReturnAfterSuspensionOffset = currentByteCodeOffSet
            else -> {
                myState = CurrentInsn.NONE
                coroutineSuspendedState = CoroutineSuspendedState.NONE
            }
        }
    }

    override fun visitLdcInsn(value: Any?) {
        super.visitLdcInsn(value)
        when {
            // reached resumed location or skipping arguments
            myState == CurrentInsn.NONE && reachedResumedLocation() -> myState = CurrentInsn.SUSPEND_METHOD_ARGS
            myState == CurrentInsn.SUSPEND_METHOD_ARGS -> {} // skip function arguments
            else -> {
                myState = CurrentInsn.NONE
                coroutineSuspendedState = CoroutineSuspendedState.NONE
            }
        }
    }


    override fun visitJumpInsn(opcode: Int, label: Label?) {
        super.visitJumpInsn(opcode, label)
        when {
            myState == CurrentInsn.ALOAD_SUSPEND_RESULT_BEFORE_COMPARE && opcode == Opcodes.IF_ACMPNE -> {
                myState = CurrentInsn.IF_ACMPNE
                nextCallLabel = label
            }
            else -> {
                myState = CurrentInsn.NONE
                coroutineSuspendedState = CoroutineSuspendedState.NONE
            }
        }
    }

    override fun visitIntInsn(opcode: Int, operand: Int) {
        super.visitIntInsn(opcode, operand)
        when {
            myState == CurrentInsn.NONE && reachedResumedLocation() -> myState = CurrentInsn.SUSPEND_METHOD_ARGS
            myState == CurrentInsn.SUSPEND_METHOD_ARGS -> {} // skip function arguments
            else -> {
                myState = CurrentInsn.NONE
                coroutineSuspendedState = CoroutineSuspendedState.NONE
            }
        }
    }

    override fun visitIincInsn(varIndex: Int, increment: Int) {
        super.visitIincInsn(varIndex, increment)
        when {
            myState == CurrentInsn.NONE && reachedResumedLocation() -> myState = CurrentInsn.SUSPEND_METHOD_ARGS
            myState == CurrentInsn.SUSPEND_METHOD_ARGS -> {} // skip function arguments
            else -> {
                myState = CurrentInsn.NONE
                coroutineSuspendedState = CoroutineSuspendedState.NONE
            }
        }
    }

    override fun visitVarInsn(opcode: Int, `var`: Int) {
        super.visitVarInsn(opcode, `var`)
        when {
            coroutineSuspendedState == CoroutineSuspendedState.INVOKE_GET_COROUTINE_SUSPENDED && opcode == Opcodes.ASTORE -> coroutineSuspendedLocalVarIndex = `var`
            myState == CurrentInsn.NONE && reachedResumedLocation() -> myState = CurrentInsn.SUSPEND_METHOD_ARGS
            myState == CurrentInsn.SUSPEND_METHOD_ARGS -> {} // skip function arguments
            myState == CurrentInsn.DUP && opcode == Opcodes.ALOAD && `var` == coroutineSuspendedLocalVarIndex -> myState = CurrentInsn.ALOAD_SUSPEND_RESULT_BEFORE_COMPARE
            myState == CurrentInsn.IF_ACMPNE && opcode == Opcodes.ALOAD && `var` == coroutineSuspendedLocalVarIndex -> myState = CurrentInsn.ALOAD_SUSPEND_RESULT_FOR_SUSPEND_RETURN
            else -> {
                myState = CurrentInsn.NONE
                coroutineSuspendedState = CoroutineSuspendedState.NONE
            }
        }
    }

    override fun visitFieldInsn(opcode: Int, owner: String?, name: String?, descriptor: String?) {
        super.visitFieldInsn(opcode, owner, name, descriptor)
        when {
            myState == CurrentInsn.NONE && reachedResumedLocation() -> myState = CurrentInsn.SUSPEND_METHOD_ARGS
            myState == CurrentInsn.SUSPEND_METHOD_ARGS && opcode == Opcodes.PUTFIELD && checkContinuationLabelField(resumeLocation, name, descriptor, owner) -> myState = CurrentInsn.PUTFIELD_CONTINUATION_LABEL
            myState == CurrentInsn.SUSPEND_METHOD_ARGS -> {} // skip function arguments
            else -> {
                myState = CurrentInsn.NONE
                coroutineSuspendedState = CoroutineSuspendedState.NONE
            }
        }
    }

    override fun visitLabel(label: Label?) {
        super.visitLabel(label)
        if (myState == CurrentInsn.NONE && reachedResumedLocation()) myState = CurrentInsn.SUSPEND_METHOD_ARGS
        if (label == nextCallLabel) {
            nextCallOffset = currentByteCodeOffSet
        }
    }

    private fun reachedResumedLocation() = currentByteCodeOffSet.toLong() == resumeLocation.codeIndex()

    private fun isGetCoroutineSuspended(name: String?, owner: String?): Boolean {
        if (name == null || owner == null) return false
        return name == "getCOROUTINE_SUSPENDED" && owner == "kotlin/coroutines/intrinsics/IntrinsicsKt"
    }

    private fun isSuspendFunction(name: String?, descriptor: String?): Boolean {
        if (name == null || descriptor == null) return false
        return descriptor.contains(CONTINUATION_TYPE.toString()) && name != "<init>"
    }
}

fun getLocationOfCoroutineSuspendReturn(resumedLocation: Location?): Location? {
    val resumedMethod = resumedLocation?.safeMethod() ?: return null;
    if (DexDebugFacility.isDex(resumedMethod.virtualMachine())) {
        return null
    }
    val visitor = CoroutineStateMachineVisitor(resumedMethod, resumedLocation)
    MethodBytecodeUtil.visit(resumedMethod, visitor, true)
    return resumedMethod.locationOfCodeIndex(visitor.firstReturnAfterSuspensionOffset.toLong())
}

fun getLocationOfNextInstructionAfterResume(resumeLocation: Location?): Location? {
    val resumedMethod = resumeLocation?.safeMethod() ?: return null
    if (DexDebugFacility.isDex(resumedMethod.virtualMachine())) {
        return null
    }
    val visitor = CoroutineStateMachineVisitor(resumedMethod, resumeLocation)
    MethodBytecodeUtil.visit(resumedMethod, visitor, true)
    return resumedMethod.locationOfCodeIndex(visitor.nextCallOffset.toLong())
}

fun isOneLineMethod(location: Location): Boolean {
    val method = location.safeMethod() ?: return false
    val allLineLocations = method.safeAllLineLocations()
    if (allLineLocations.isEmpty()) return false
    if (allLineLocations.size == 1) return true

    val inlineFunctionBorders = method.getInlineFunctionAndArgumentVariablesToBordersMap().values
    return allLineLocations
        .mapNotNull { loc ->
            if (!isKotlinFakeLineNumber(loc) &&
                !inlineFunctionBorders.any { loc in it })
                loc.lineNumber()
            else
                null
        }
        .toHashSet()
        .size == 1
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
        .filter { JvmAbi.isFakeLocalVariableForInline(it.name()) }
}

val DebugProcessImpl.canRunEvaluation: Boolean
    get() = suspendManager.pausedContext != null

val String.isInlineFunctionMarkerVariableName: Boolean
    get() = startsWith(JvmAbi.LOCAL_VARIABLE_NAME_PREFIX_INLINE_FUNCTION)

val String.isInlineLambdaMarkerVariableName: Boolean
    get() = startsWith(JvmAbi.LOCAL_VARIABLE_NAME_PREFIX_INLINE_ARGUMENT)
