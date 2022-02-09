// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.debugger.stepping.smartStepInto

import org.jetbrains.org.objectweb.asm.Handle
import org.jetbrains.org.objectweb.asm.Label
import org.jetbrains.org.objectweb.asm.MethodVisitor
import org.jetbrains.org.objectweb.asm.Opcodes

/**
 * This class pipes all visit*Insn calls to the [reportOpcode] function. The derived class can process all visited opcodes
 * by overriding only [reportOpcode] function.
 */
open class OpcodeReportingMethodVisitor(
    private val delegate: OpcodeReportingMethodVisitor? = null
) : MethodVisitor(Opcodes.API_VERSION, delegate) {
    protected open fun reportOpcode(opcode: Int) {
        delegate?.reportOpcode(opcode)
    }

    override fun visitInsn(opcode: Int) =
        reportOpcode(opcode)
    override fun visitLdcInsn(value: Any?) =
        reportOpcode(Opcodes.LDC)
    override fun visitLookupSwitchInsn(dflt: Label?, keys: IntArray?, labels: Array<out Label>?) =
        reportOpcode(Opcodes.LOOKUPSWITCH)
    override fun visitMultiANewArrayInsn(descriptor: String?, numDimensions: Int) =
        reportOpcode(Opcodes.MULTIANEWARRAY)
    override fun visitIincInsn(variable: Int, increment: Int) =
        reportOpcode(Opcodes.IINC)
    override fun visitIntInsn(opcode: Int, operand: Int) =
        reportOpcode(opcode)
    override fun visitVarInsn(opcode: Int, variable: Int) =
        reportOpcode(opcode)
    override fun visitTypeInsn(opcode: Int, type: String?) =
        reportOpcode(opcode)
    override fun visitFieldInsn(opcode: Int, owner: String?, name: String?, descriptor: String?) =
        reportOpcode(opcode)
    override fun visitJumpInsn(opcode: Int, label: Label?) =
        reportOpcode(opcode)
    override fun visitTableSwitchInsn(min: Int, max: Int, dflt: Label?, vararg labels: Label?) =
        reportOpcode(Opcodes.TABLESWITCH)
    override fun visitMethodInsn(opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean) =
        reportOpcode(opcode)
    override fun visitInvokeDynamicInsn(
        name: String?,
        descriptor: String?,
        bootstrapMethodHandle: Handle?,
        vararg bootstrapMethodArguments: Any?
    ) = reportOpcode(Opcodes.INVOKEDYNAMIC)
}
