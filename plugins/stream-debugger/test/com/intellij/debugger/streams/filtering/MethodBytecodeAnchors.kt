// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.filtering

import com.intellij.debugger.jdi.MethodBytecodeUtil
import com.sun.jdi.Method
import org.jetbrains.org.objectweb.asm.MethodVisitor
import org.jetbrains.org.objectweb.asm.Opcodes

/**
 * A symbolic description of a bytecode position inside a JDI [Method].
 * Resolved to a concrete bytecode offset by [resolveSymbolicPositions] after disassembling the method,
 * so tests do not hardcode numeric offsets that may change between JDK versions.
 */
sealed interface BytecodePosition

/**
 * The PC right before the [occurrence]-th `invoke*` of a method named [name] executes - the call has not run yet
 *
 * [className] is the declaring class in JVM internal-name form (e.g. `java/util/stream/IntStream`). Simple name like `IntStream` also matches.
 * It is only necessary to disambiguate same-named methods (usually [name] + [occurrence] is enough).
 */
data class BeforeInvoke(val name: String, val occurrence: Int = 0, val className: String? = null) : BytecodePosition {
  override fun toString(): String = "before ${describeInvoke(name, occurrence, className)}"
}

/**
 * Same as [BeforeInvoke] but PC right after the call returns
 */
data class AfterInvoke(val name: String, val occurrence: Int = 0, val className: String? = null) : BytecodePosition {
  override fun toString(): String = "after ${describeInvoke(name, occurrence, className)}"
}

private fun describeInvoke(name: String, occurrence: Int, className: String?): String = buildString {
  className?.let { append(it.substringAfterLast('/')).append('.') }
  append(name)
  if (occurrence > 0) append('#').append(occurrence)
}

/**
 * [beforeOffset] - the invoke's own offset (PC right before the call executes)
 * [afterOffset] - the offset of the instruction immediately following it (PC once the call returns).
 * [className] is the callee's declaring class in JVM internal-name form (e.g. `java/util/stream/IntStream`).
 */
data class InvokeInsn(
  val beforeOffset: Int,
  val afterOffset: Int,
  val className: String,
  val name: String,
  val descriptor: String,
)

internal fun extractInvokesFromBytecode(method: Method): List<InvokeInsn> {
  val vm = method.virtualMachine()
  require(vm.canGetBytecodes() && vm.canGetConstantPool()) { "VM doesn't support bytecode and constant pool access" }
  val invokes = ArrayList<InvokeInsn>()
  val visitor = object : MethodVisitor(Opcodes.API_VERSION), MethodBytecodeUtil.InstructionOffsetReader {
    private var currentOffset = -1
    private var pendingInvoke: PendingInvoke? = null

    override fun readBytecodeInstructionOffset(offset: Int) {
      // Fires right before each instruction: this instruction is exactly the one right after a pending invoke
      pendingInvoke?.let { invokes.add(it.withAfterOffset(offset)) }
      pendingInvoke = null
      currentOffset = offset
    }

    override fun visitMethodInsn(opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean) {
      pendingInvoke = PendingInvoke(currentOffset, owner, name, descriptor)
    }
  }
  MethodBytecodeUtil.visit(method, visitor, false)
  return invokes
}

private class PendingInvoke(val beforeOffset: Int, val className: String, val name: String, val descriptor: String) {
  fun withAfterOffset(afterOffset: Int): InvokeInsn = InvokeInsn(beforeOffset, afterOffset, className, name, descriptor)
}

fun resolveSymbolicPositions(position: BytecodePosition, invokes: List<InvokeInsn>): Long = when (position) {
  is BeforeInvoke -> findInvoke(invokes, position.name, position.occurrence, position.className).beforeOffset.toLong()
  is AfterInvoke -> findInvoke(invokes, position.name, position.occurrence, position.className).afterOffset.toLong()
}

private fun findInvoke(invokes: List<InvokeInsn>, name: String, occurrence: Int, className: String?): InvokeInsn {
  val matches = invokes.filter { invoke ->
    invoke.name == name && (className == null || invoke.className == className || invoke.className.endsWith(className))
  }
  require(occurrence in matches.indices) {
    "No invoke of '$name'${className?.let { " (class $it)" } ?: ""} with occurrence #$occurrence; found ${matches.size}"
  }
  return matches[occurrence]
}
