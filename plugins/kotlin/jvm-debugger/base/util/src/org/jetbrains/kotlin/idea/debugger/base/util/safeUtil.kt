// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.base.util

import com.intellij.debugger.NoDataException
import com.intellij.debugger.PositionManager
import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.DebugProcess.JAVA_STRATUM
import com.intellij.debugger.engine.PositionManagerAsync
import com.intellij.debugger.engine.evaluation.AbsentInformationEvaluateException
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.jdi.StackFrameProxy
import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.debugger.jdi.LocalVariableProxyImpl
import com.intellij.debugger.jdi.StackFrameProxyImpl
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.sun.jdi.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.codegen.inline.KOTLIN_STRATA_NAME

fun StackFrameProxyImpl.safeVisibleVariables(): List<LocalVariableProxyImpl> {
    return wrapEvaluateException { visibleVariables() } ?: emptyList()
}

fun StackFrameProxyImpl.safeArgumentValues(): List<Value> {
    return wrapEvaluateException { argumentValues } ?: emptyList()
}

fun StackFrameProxyImpl.safeVisibleVariableByName(name: String): LocalVariableProxyImpl? {
    return wrapEvaluateException { visibleVariableByName(name) }
}

fun StackFrame.safeVisibleVariables(): List<LocalVariable> {
    return wrapAbsentInformationException { visibleVariables() } ?: emptyList()
}

fun Method.safeAllLineLocations(): List<Location> {
    return DebuggerUtilsEx.allLineLocations(this) ?: emptyList()
}

fun ReferenceType.safeAllLineLocations(): List<Location> {
    return DebuggerUtilsEx.allLineLocations(this) ?: emptyList()
}

fun ClassType.safeAllInterfaces(): List<InterfaceType> {
    return wrapClassNotPreparedException { allInterfaces() } ?: emptyList()
}

fun ReferenceType.safeSourceName(): String? {
    return wrapAbsentInformationException { sourceName() }
}

fun ReferenceType.safeFields(): List<Field> {
    return try {
        fields()
    } catch (e: ClassNotPreparedException) {
        emptyList()
    }
}

fun Method.safeReturnType(): Type? {
    return wrapClassNotLoadedException { returnType() }
}

fun Method.safeLocationsOfLine(line: Int): List<Location> {
    return wrapAbsentInformationException { locationsOfLine(line) } ?: emptyList()
}

fun Method.safeVariables(): List<LocalVariable>? {
    return wrapAbsentInformationException { variables() }
}

fun Method.safeArguments(): List<LocalVariable>? {
    return wrapAbsentInformationException { arguments() }
}

fun StackFrameProxy.safeLocation(): Location? {
    return wrapEvaluateException { this.location() }
}

fun StackFrameProxy.safeStackFrame(): StackFrame? {
    return wrapEvaluateException { this.stackFrame }
}

fun StackFrameProxyImpl.safeThreadProxy(): ThreadReferenceProxyImpl? {
    return wrapEvaluateException { this.threadProxy() }
}

fun StackFrameProxyImpl.safeThisObject(): ObjectReference? {
    return wrapEvaluateException { thisObject() }
}

fun Location.safeSourceName(): String? = DebuggerUtilsEx.getSourceName(this, null)

fun Location.safeSourceName(stratum: String): String? {
    return wrapIllegalArgumentException { wrapAbsentInformationException { this.sourceName(stratum) } }
}

fun Location.safeSourcePath(stratum: String): String? {
    return wrapIllegalArgumentException { wrapAbsentInformationException { this.sourcePath(stratum) } }
}

fun Location.safeLineNumber(): Int = DebuggerUtilsEx.getLineNumber(this, false)

fun Location.safeLineNumber(stratum: String): Int {
    return try {
        lineNumber(stratum)
    } catch (e: InternalError) {
        -1
    } catch (e: IllegalArgumentException) {
        -1
    }
}

fun Location.safeKotlinPreferredLineNumber(): Int {
    val kotlinLineNumber = safeLineNumber(KOTLIN_STRATA_NAME)
    if (kotlinLineNumber > 0) {
        return kotlinLineNumber
    }

    return safeLineNumber(JAVA_STRATUM)
}

fun Location.safeMethod(): Method? = DebuggerUtilsEx.getMethod(this)

fun LocalVariableProxyImpl.safeType(): Type? {
    return wrapClassNotLoadedException { type }
}

fun Field.safeType(): Type? {
    return wrapClassNotLoadedException { type() }
}

fun ValueDescriptorImpl.safeCalcValue(context: EvaluationContextImpl): Value? {
    return wrapEvaluateException { calcValue(context) }
}

@RequiresBlockingContext
@ApiStatus.Internal
fun PositionManager.safeGetSourcePosition(location: Location): SourcePosition? {
    return try {
        getSourcePosition(location)
    } catch (_: NoDataException) {
        null
    }
}

@ApiStatus.Internal
suspend fun PositionManagerAsync.safeGetSourcePositionAsync(location: Location): SourcePosition? {
    return try {
        getSourcePositionAsync(location)
    } catch (_: NoDataException) {
        null
    }
}

inline fun <T> wrapEvaluateException(block: () -> T): T? {
    return try {
        block()
    } catch (e: EvaluateException) {
        null
    }
}

inline fun <T> wrapIllegalArgumentException(block: () -> T): T? {
    return try {
        block()
    } catch (e: IllegalArgumentException) {
        null
    }
}

private inline fun <T> wrapAbsentInformationException(block: () -> T): T? {
    return try {
        block()
    } catch (e: AbsentInformationException) {
        null
    } catch (e: AbsentInformationEvaluateException) {
        null
    } catch (e: InternalException) {
        null
    } catch (e: UnsupportedOperationException) {
        null
    }
}

private inline fun <T> wrapClassNotLoadedException(block: () -> T): T? {
    return try {
        block()
    } catch (e: ClassNotLoadedException) {
        null
    }
}

private inline fun <T> wrapClassNotPreparedException(block: () -> T): T? {
    return try {
        block()
    } catch (e: ClassNotPreparedException) {
        null
    }
}
