// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:JvmName("DebuggerUtil")

package org.jetbrains.kotlin.idea.debugger.core

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.DebuggerManagerThreadImpl
import com.intellij.debugger.engine.events.DebuggerCommandImpl
import com.intellij.debugger.impl.DebuggerContextImpl
import com.intellij.debugger.impl.DebuggerUtilsAsync
import com.intellij.debugger.jdi.StackFrameProxyImpl
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiElement
import com.sun.jdi.*
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.codegen.coroutines.INVOKE_SUSPEND_METHOD_NAME
import org.jetbrains.kotlin.codegen.inline.KOTLIN_STRATA_NAME
import org.jetbrains.kotlin.codegen.inline.isFakeLocalVariableForInline
import org.jetbrains.kotlin.codegen.topLevelClassAsmType
import org.jetbrains.kotlin.idea.base.psi.getLineEndOffset
import org.jetbrains.kotlin.idea.base.psi.getLineStartOffset
import org.jetbrains.kotlin.idea.base.psi.getTopmostElementAtOffset
import org.jetbrains.kotlin.idea.base.util.KOTLIN_FILE_EXTENSIONS
import org.jetbrains.kotlin.idea.debugger.base.util.*
import org.jetbrains.kotlin.idea.debugger.core.DebuggerUtils.getBorders
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
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

fun <T : Any> DebugProcessImpl.invokeInManagerThread(f: (DebuggerContextImpl) -> T?): T? {
    var result: T? = null
    val command: DebuggerCommandImpl = object : DebuggerCommandImpl() {
        override fun action() {
            result = f(debuggerContext)
        }
    }

    when {
        DebuggerManagerThreadImpl.isManagerThread() ->
            managerThread.invoke(command)
        else ->
            managerThread.invokeAndWait(command)
    }

    return result
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
    return signature.contains(continuationAsmType.toString()) ||
          (method.name() == INVOKE_SUSPEND_METHOD_NAME && signature == INVOKE_SUSPEND_SIGNATURE)
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
    return firstLocation.safeLineNumber() == location.safeLineNumber()
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
        if (location.lineNumber("Kotlin") == 1 &&
            location.sourceName("Kotlin") == "fake.kt" &&
            Path.of(location.sourcePath("Kotlin")) == Path.of("kotlin/jvm/internal/FakeKt")
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

val KtFunction.isLambdaOrAnonymous: Boolean
    get() = this is KtFunctionLiteral || name == null
