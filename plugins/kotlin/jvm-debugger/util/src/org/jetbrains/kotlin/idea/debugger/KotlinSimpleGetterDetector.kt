// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.debugger

import com.intellij.debugger.jdi.MethodBytecodeUtil
import com.sun.jdi.Method
import org.jetbrains.org.objectweb.asm.MethodVisitor
import org.jetbrains.org.objectweb.asm.Opcodes

object KotlinSimpleGetterDetector {
    fun Method.isSimpleGetter() =
        isSimpleMemberVariableGetter() || isSimpleStaticVariableGetter()

    private fun Method.isSimpleStaticVariableGetter(): Boolean {
        if (bytecodes().size != 4) {
            return false
        }

        val methodBytecodeVerifier = object : MethodBytecodeVerifier() {
            override fun verify(opcode: Int, position: Int) =
                when(position) {
                    0 -> opcode == Opcodes.GETSTATIC
                    1 -> opcode in Opcodes.IRETURN..Opcodes.ARETURN
                    else -> false
                }
        }

        MethodBytecodeUtil.visit(this, methodBytecodeVerifier, false)
        return methodBytecodeVerifier.result && methodBytecodeVerifier.processedOpcodes == 2
    }

    private fun Method.isSimpleMemberVariableGetter(): Boolean {
        if (bytecodes().size != 5) {
            return false
        }

        val methodBytecodeVerifier = object : MethodBytecodeVerifier() {
            override fun verify(opcode: Int, position: Int) =
                when(position) {
                    0 -> opcode == Opcodes.ALOAD
                    1 -> opcode == Opcodes.GETFIELD
                    2 -> opcode in Opcodes.IRETURN..Opcodes.ARETURN
                    else -> false
                }
        }

        MethodBytecodeUtil.visit(this, methodBytecodeVerifier, false)
        return methodBytecodeVerifier.result && methodBytecodeVerifier.processedOpcodes == 3
    }

    private abstract class MethodBytecodeVerifier : MethodVisitor(Opcodes.API_VERSION) {
        var processedOpcodes = 0
        var result = true

        protected abstract fun verify(opcode: Int, position: Int): Boolean

        private fun visitOpcode(opcode: Int) {
            if (!verify(opcode, processedOpcodes)) {
                result = false
            }
            processedOpcodes += 1
        }

        override fun visitVarInsn(opcode: Int, variable: Int) = visitOpcode(opcode)
        override fun visitFieldInsn(opcode: Int, owner: String?, name: String?, descriptor: String?) = visitOpcode(opcode)
        override fun visitInsn(opcode: Int) = visitOpcode(opcode)
    }
}
