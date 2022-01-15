// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.debugger

import com.intellij.debugger.jdi.MethodBytecodeUtil
import com.sun.jdi.Method
import org.jetbrains.org.objectweb.asm.Label
import org.jetbrains.org.objectweb.asm.MethodVisitor
import org.jetbrains.org.objectweb.asm.Opcodes

fun Method.isSimpleGetter() =
    isSimpleMemberVariableGetter() ||
    isSimpleStaticVariableGetter() ||
    isJVMStaticVariableGetter()

fun Method.isLateinitVariableGetter() =
    isOldBackendLateinitVariableGetter() ||
    isIRBackendLateinitVariableGetter() ||
    isIRBackendLateinitVariableGetterReturningAny()

fun Method.isOldBackendLateinitVariableGetter() =
    verifyMethod(14,
        intArrayOf(
            Opcodes.ALOAD,
            Opcodes.GETFIELD,
            Opcodes.DUP,
            Opcodes.IFNONNULL,
            Opcodes.LDC,
            Opcodes.INVOKESTATIC
        )
    )

fun Method.isIRBackendLateinitVariableGetterReturningAny() =
    verifyMethod(
        expectedNumOfBytecodes = 19,
        MethodBytecodeVerifierFromArray(lateinitVarReturningAnyBytecodes)
    )

fun Method.isIRBackendLateinitVariableGetter() =
    verifyMethod(
        expectedNumOfBytecodes = 17,
        MethodBytecodeVerifierFromArray(lateinitVarPropertyBytecodes)
    )

private val commonLateinitVarPropertyBytecodes =
    intArrayOf(
        Opcodes.ALOAD,
        Opcodes.GETFIELD,
        Opcodes.DUP,
        Opcodes.IFNULL,
        Opcodes.ARETURN,
        Opcodes.POP,
        Opcodes.LDC,
        Opcodes.INVOKESTATIC,
    )

private val lateinitVarReturningAnyBytecodes =
    commonLateinitVarPropertyBytecodes + intArrayOf(Opcodes.GETSTATIC, Opcodes.ARETURN)

private val lateinitVarPropertyBytecodes =
    commonLateinitVarPropertyBytecodes + intArrayOf(Opcodes.ACONST_NULL, Opcodes.ARETURN)

private fun Method.isSimpleStaticVariableGetter() =
    verifyMethod(4, intArrayOf(Opcodes.GETSTATIC))

private fun Method.isSimpleMemberVariableGetter() =
    verifyMethod(5, intArrayOf(Opcodes.ALOAD, Opcodes.GETFIELD))

private fun Method.isJVMStaticVariableGetter() =
    verifyMethod(7, object : MethodBytecodeVerifier(3) {
        override fun verify(opcode: Int, position: Int) =
            when(position) {
                0 -> opcode == Opcodes.GETSTATIC
                1 -> opcode == Opcodes.GETSTATIC || opcode == Opcodes.INVOKEVIRTUAL
                2 -> opcode.isReturnOpcode()
                else -> false
            }
    })

private fun Method.verifyMethod(
    expectedNumOfBytecodes: Int,
    opcodes: IntArray
) = verifyMethod(expectedNumOfBytecodes, MethodBytecodeVerifierWithReturnOpcode(opcodes))

private fun Method.verifyMethod(
    expectedNumOfBytecodes: Int,
    methodBytecodeVerifier: MethodBytecodeVerifier
): Boolean {
    if (bytecodes().size != expectedNumOfBytecodes) {
        return false
    }
    MethodBytecodeUtil.visit(this, methodBytecodeVerifier, false)
    return methodBytecodeVerifier.getResult()
}

private class MethodBytecodeVerifierWithReturnOpcode(val opcodes: IntArray) : MethodBytecodeVerifier(opcodes.size + 1) {
    override fun verify(opcode: Int, position: Int) =
        when {
            position > opcodes.size -> false
            position == opcodes.size -> opcode.isReturnOpcode()
            else -> opcode == opcodes[position]
        }
}

private class MethodBytecodeVerifierFromArray(val opcodes: IntArray) : MethodBytecodeVerifier(opcodes.size) {
    override fun verify(opcode: Int, position: Int) =
        when {
            position >= opcodes.size -> false
            else -> opcode == opcodes[position]
        }
}

private fun Int.isReturnOpcode() = this in Opcodes.IRETURN..Opcodes.ARETURN

private abstract class MethodBytecodeVerifier(val expectedNumOfProcessedOpcodes: Int) : MethodVisitor(Opcodes.API_VERSION) {
    var processedOpcodes = 0
    var opcodesMatched = true

    protected abstract fun verify(opcode: Int, position: Int): Boolean

    private fun visitOpcode(opcode: Int) {
        if (!verify(opcode, processedOpcodes)) {
            opcodesMatched = false
        }
        processedOpcodes += 1
    }

    fun getResult() = opcodesMatched && processedOpcodes == expectedNumOfProcessedOpcodes

    override fun visitVarInsn(opcode: Int, variable: Int) = visitOpcode(opcode)
    override fun visitFieldInsn(opcode: Int, owner: String?, name: String?, descriptor: String?) = visitOpcode(opcode)
    override fun visitInsn(opcode: Int) = visitOpcode(opcode)
    override fun visitIntInsn(opcode: Int, operand: Int) = visitOpcode(opcode)
    override fun visitTypeInsn(opcode: Int, type: String?) = visitOpcode(opcode)
    override fun visitJumpInsn(opcode: Int, label: Label?) = visitOpcode(opcode)
    override fun visitLdcInsn(value: Any?) = visitOpcode(Opcodes.LDC)
    override fun visitMethodInsn(
        opcode: Int,
        owner: String?,
        name: String?,
        descriptor: String?,
        isInterface: Boolean
    ) = visitOpcode(opcode)
}
