// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.filtering

import com.intellij.debugger.engine.DebuggerManagerThreadImpl
import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.debugger.jdi.MethodBytecodeUtil
import com.intellij.debugger.streams.core.wrapper.StreamChain
import com.intellij.debugger.streams.psi.findPsiMethodCall
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Document
import com.intellij.psi.JavaRecursiveElementVisitor
import com.intellij.psi.PsiAnonymousClass
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLambdaExpression
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.util.ClassUtil
import com.intellij.xdebugger.XSourcePosition
import com.sun.jdi.Method
import org.jetbrains.org.objectweb.asm.Label
import org.jetbrains.org.objectweb.asm.MethodVisitor
import org.jetbrains.org.objectweb.asm.Opcodes

/**
 * Filters [chains] leaving only the ones still traceable from the current position: a chain is traceable while execution
 * has not reached its first operator (the first intermediate call, or the terminal call when there are no intermediate
 * ones). The producer/qualifier and its arguments are not operators.
 *
 * [classifyBySourcePosition] decides the chains lying fully above/below the stop line; the ones straddling it are decided
 * precisely by matching each chain's first operator ([computeStreamCallMappings]) against the method bytecode
 * ([matchOperatorOffsets]). [contextElement] and [position] describe where execution is stopped. Must be called on the
 * debugger manager thread.
 */
internal suspend fun filterTraceableStreams(
  chains: List<StreamChain>,
  position: XSourcePosition,
  contextElement: PsiElement,
  method: Method,
  bytecodeOffset: Long,
): List<StreamChain> {
  DebuggerManagerThreadImpl.assertIsManagerThread()
  if (chains.isEmpty()) return chains

  val classified = readAction {
    val document = contextElement.containingFile?.fileDocument ?: return@readAction null
    classifyBySourcePosition(chains, document, position.line)
  } ?: return chains
  if (classified.uncertain.isEmpty()) return classified.traceable

  val bytecodeFiltered = filterByBytecodeOffset(contextElement, classified.uncertain, method, bytecodeOffset)
  return classified.traceable + bytecodeFiltered
}

/**
 * Fast line-based filter to remove irrelevant chains that are located entirely above the current debugger position.
 */
private fun classifyBySourcePosition(
  chains: List<StreamChain>,
  document: Document,
  currentLine: Int,
): ClassifiedStreams {
  val traceable = mutableListOf<StreamChain>()
  val notTraceable = mutableListOf<StreamChain>()
  val uncertain = mutableListOf<StreamChain>()
  for (chain in chains) {
    val startLine = document.getLineNumber(chain.qualifierExpression.textRange.startOffset)
    val endLine = document.getLineNumber(chain.terminationCall.textRange.endOffset)
    when {
      endLine < currentLine -> notTraceable += chain
      startLine > currentLine -> traceable += chain
      else -> uncertain += chain
    }
  }
  return ClassifiedStreams(traceable, notTraceable, uncertain)
}

private data class ClassifiedStreams(
  val traceable: List<StreamChain>,
  val notTraceable: List<StreamChain>,
  val uncertain: List<StreamChain>,
)

/**
 * Matches the first operator of each chain against the bytecode of [method]
 * and keeps the chain if that operator's method call is ahead of [bytecodeOffset].
 *
 * Note: whenever the PSI and the bytecode cannot be matched reliably, the chain is kept.
 * This fallback is chosen deliberately because the probability of a user hitting a debugger
 * in an expression containing multiple streams on one line is quite low.
 */
private suspend fun filterByBytecodeOffset(
  contextElement: PsiElement,
  chains: List<StreamChain>,
  method: Method,
  bytecodeOffset: Long,
): List<StreamChain> {
  val mappings = readAction { computeStreamCallMappings(contextElement, chains) }
  // Nothing to match against the bytecode, so return what the line number-based filtering already decided
  if (mappings.callIdToChain.isEmpty()) return mappings.candidates

  val firstOperatorOffsets = matchOperatorOffsets(method, mappings.callIdToChain, mappings.lineRange)
  return mappings.candidates.filter { chain ->
    val offset = firstOperatorOffsets[chain]
    // A null offset means that the first operator was found in the PSI wasn't matched with the bytecode
    // (ex. because we built a wrong ASM signature for the method)
    offset == null || offset >= bytecodeOffset
  }
}

/**
 * Computes mapping of `call identifier => stream chain to which it corresponds`
 *
 * The unique call identifier is constructed as `method name + ASM signature + sequential call number within the method`.
 *
 * We build mappings only for the first operator in each chain
 * because it is enough for us to know whether the stream execution has started or not.
 *
 * All the necessary information from the source code is precomputed at this stage to avoid mixing bytecode analysis and PSI parsing on DMT.
 */
private fun computeStreamCallMappings(contextElement: PsiElement, chains: List<StreamChain>): StreamCallMappings {
  val psiFile = contextElement.containingFile ?: return StreamCallMappings(chains, emptyMap(), IntRange.EMPTY)
  // `DebuggerUtilsEx.getContainingMethod` returns `null` when the position is not inside a method, a lambda or an initializer.
  // In practice this is a field initializer that we do not support now.
  val host = DebuggerUtilsEx.getContainingMethod(contextElement) ?: return StreamCallMappings(chains, emptyMap(), IntRange.EMPTY)
  val document = psiFile.fileDocument

  val firstOperatorCallToChain = LinkedHashMap<PsiMethodCallExpression, StreamChain>()
  val candidates = mutableListOf<StreamChain>()
  for (chain in chains) {
    val firstOperator = chain.intermediateCalls.firstOrNull() ?: chain.terminationCall
    val firstOperatorCall = findPsiMethodCall(psiFile, firstOperator.textRange)
    when {
      // For Java the operator range is exactly `PsiMethodCallExpression.getTextRange()` and `findPsiMethodCall`
      // requires an exact match, so the call is normally found.
      // Null means the PSI no longer matches the ranges the chain was built from: chains are collected in a single `smartReadAction`,
      // while we are in a separate `readAction` on the debugger thread, and the file could have been edited in between.
      // We cannot analyze such chain, so keep it.
      firstOperatorCall == null -> candidates += chain
      // The operator belongs to a JVM method other than the one we are stopped in.
      // The most common case is a stop inside a lambda inside the stream chain -
      // lambdas are compiled into a separate method, so supporting such cases will significantly complicate the algorithm.
      DebuggerUtilsEx.getContainingMethod(firstOperatorCall) != host -> Unit // operator in a nested method is already executing
      else -> {
        candidates += chain
        firstOperatorCallToChain[firstOperatorCall] = chain
      }
    }
  }

  val hostBody = DebuggerUtilsEx.getBody(host)
  val lineRange = computeLineBoundsForChains(document, chains)

  // hostBody may be null: an abstract or native method, where execution cannot stop, or PSI broken by an edit made while the session was paused.
  if (firstOperatorCallToChain.isEmpty() || hostBody == null) return StreamCallMappings(candidates, emptyMap(), lineRange)

  val signatureCounter = HashMap<String, Int>()
  val callIdToChain = HashMap<String, StreamChain>()
  visitMethodCallsInExecutionOrder(hostBody) { methodCall ->
    if (document.getLineNumber(methodCall.textRange.startOffset) !in lineRange) return@visitMethodCallsInExecutionOrder
    val callee = methodCall.resolveMethod() ?: return@visitMethodCallsInExecutionOrder
    // Restoring the descriptor from the PSI may fail.
    // For plain calls like the Stream API this is exactly what javac emits, but it diverges in:
    // - synthetic access$NNN bridges
    // - Kotlin ($default overloads have another descriptor, inline functions produce no instruction at all)
    // It's not a big deal for us if we fail - after all, it's a heuristic and the chain stays visible
    val signature = callee.name + ClassUtil.getAsmMethodSignature(callee)
    val ordinal = signatureCounter.compute(signature) { _, value -> if (value == null) 0 else value + 1 }!!

    val chain = firstOperatorCallToChain[methodCall]
    if (chain != null) {
      callIdToChain[invocationId(signature, ordinal)] = chain
    }
  }
  return StreamCallMappings(candidates, callIdToChain, lineRange)
}

private fun invocationId(signature: String, ordinal: Int): String = "$signature#$ordinal"

private class StreamCallMappings(
  val candidates: List<StreamChain>,
  // JVM signature of the operator call -> stream chain
  val callIdToChain: Map<String, StreamChain>,
  val lineRange: IntRange,
)

private fun computeLineBoundsForChains(document: Document, chains: List<StreamChain>): IntRange {
  var min = Int.MAX_VALUE
  var max = Int.MIN_VALUE
  for (chain in chains) {
    min = minOf(min, document.getLineNumber(chain.qualifierExpression.textRange.startOffset))
    max = maxOf(max, document.getLineNumber(chain.terminationCall.textRange.endOffset))
  }
  return min..max
}

/**
 * Scans the bytecode of [method] and, numbering the `invoke*` instructions on lines within [lineRange] exactly as
 * [computeStreamCallMappings] numbers the PSI method calls.
 *
 * @returns the bytecode offset matching each chain's first operator
 */
private fun matchOperatorOffsets(
  method: Method,
  callIdToChain: Map<String, StreamChain>,
  lineRange: IntRange,
): Map<StreamChain, Long> {
  val vm = method.virtualMachine()
  if (!vm.canGetBytecodes() || !vm.canGetConstantPool()) return emptyMap()
  val signatureCounter = HashMap<String, Int>()
  val offsets = HashMap<StreamChain, Long>()
  val visitor = object : MethodVisitor(Opcodes.API_VERSION), MethodBytecodeUtil.InstructionOffsetReader {
    private var currentOffset = -1L
    private var currentLine = -1 // 0-based, kept in sync with visitLineNumber

    override fun readBytecodeInstructionOffset(offset: Int) {
      currentOffset = offset.toLong()
    }

    override fun visitLineNumber(line: Int, start: Label) {
      currentLine = line - 1 // JVM line numbers are 1-based
    }

    override fun visitMethodInsn(opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean) {
      if (currentLine !in lineRange) return
      val signature = name + descriptor
      val ordinal = signatureCounter.compute(signature) { _, value -> if (value == null) 0 else value + 1 }!!
      val chain = callIdToChain[invocationId(signature, ordinal)]
      if (chain != null) {
        offsets[chain] = currentOffset
      }
    }
  }
  MethodBytecodeUtil.visit(method, visitor, true)
  return offsets
}

/**
 * Visits method calls in execution (post-)order so their per-signature ordinals line up with the bytecode invoke order.
 * Lambda/class bodies are separate JVM methods, so we skip them.
 */
private inline fun visitMethodCallsInExecutionOrder(element: PsiElement, crossinline onCall: (PsiMethodCallExpression) -> Unit) {
  element.accept(object : JavaRecursiveElementVisitor() {
    override fun visitClass(aClass: PsiClass) {}
    override fun visitLambdaExpression(expression: PsiLambdaExpression) {}

    override fun visitAnonymousClass(aClass: PsiAnonymousClass) {
      aClass.argumentList?.accept(this)
    }

    override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
      expression.methodExpression.qualifierExpression?.accept(this)
      expression.argumentList.accept(this)
      onCall(expression)
    }
  })
}
