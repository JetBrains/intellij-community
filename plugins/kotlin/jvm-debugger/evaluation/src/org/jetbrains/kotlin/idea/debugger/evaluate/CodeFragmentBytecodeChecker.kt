// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.evaluate

import org.jetbrains.kotlin.idea.debugger.evaluate.compilation.CompiledCodeFragmentData
import org.jetbrains.org.objectweb.asm.*

private const val nonExistentClassName = "error/NonExistentClass"

internal class MiscompiledCodeException(message: String) : Exception(message)

internal fun checkCodeFragmentBytecode(compiledData: CompiledCodeFragmentData) {
    if (compiledData.classes.any { containsNonExistentClass(it.bytes) }) {
        throw MiscompiledCodeException("Code fragment contains references to $nonExistentClassName")
    }
}

private fun containsNonExistentClass(classBytes: ByteArray): Boolean {
    var found = false

    val reader = ClassReader(classBytes)
    val visitor: ClassVisitor = object : ClassVisitor(Opcodes.API_VERSION) {
        override fun visit(version: Int, access: Int, name: String, signature: String?, superName: String?, interfaces: Array<String>?) {
            if (nonExistentClassName == superName) {
                found = true
            }
            if (interfaces != null) {
                for (base in interfaces) {
                    if (nonExistentClassName == base) {
                        found = true
                    }
                }
            }
        }

        override fun visitField(access: Int, name: String, descriptor: String, signature: String?, value: Any?): FieldVisitor? {
            if (descriptor.contains("L$nonExistentClassName;")) {
                found = true
            }
            return super.visitField(access, name, descriptor, signature, value)
        }

        override fun visitMethod(access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<String>?): MethodVisitor {
            if (descriptor.contains("L$nonExistentClassName;")) {
                found = true
            }
            return object : MethodVisitor(Opcodes.ASM9) {
                override fun visitTypeInsn(opcode: Int, type: String?) {
                    if (nonExistentClassName == type) {
                        found = true
                    }
                }

                override fun visitFieldInsn(opcode: Int, owner: String, name: String, descriptor: String) {
                    if (nonExistentClassName == owner || descriptor.contains("L$nonExistentClassName;")) {
                        found = true
                    }
                }

                override fun visitMethodInsn(opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean) {
                    if (nonExistentClassName == owner || descriptor.contains("L$nonExistentClassName;")) {
                        found = true
                    }
                }

                override fun visitLdcInsn(cst: Any?) {
                    if (cst is Type && nonExistentClassName == cst.internalName) {
                        found = true
                    }
                }
            }
        }

        override fun visitInnerClass(name: String, outerName: String?, innerName: String?, access: Int) {
            if (nonExistentClassName == name) {
                found = true
            }
        }

        override fun visitOuterClass(owner: String, name: String?, descriptor: String?) {
            if (nonExistentClassName == owner) {
                found = true
            }
        }
    }
    reader.accept(visitor, 0)

    return found
}
