// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.debugger.evaluate.classLoading

import com.sun.jdi.ArrayReference
import com.sun.jdi.ArrayType
import com.sun.jdi.ClassLoaderReference
import com.sun.jdi.Value
import org.jetbrains.kotlin.idea.debugger.base.util.evaluate.ExecutionContext
import org.jetbrains.org.objectweb.asm.ClassReader
import org.jetbrains.org.objectweb.asm.Label
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.tree.*
import kotlin.math.min

interface ClassLoadingAdapter {
    companion object {
        private const val CHUNK_SIZE = 4096

        private val ADAPTERS = listOf(
            AndroidOClassLoadingAdapter(),
            OrdinaryClassLoadingAdapter()
        )

        fun loadClasses(context: ExecutionContext, classes: Collection<ClassToLoad>): ClassLoaderReference? {
            val mainClass = classes.firstOrNull { it.isMainClass } ?: return null

            var info = ClassInfoForEvaluator(containsAdditionalClasses = classes.size > 1)
            if (!info.containsAdditionalClasses) {
                info = analyzeClass(mainClass, info)
            }

            for (adapter in ADAPTERS) {
                if (adapter.isApplicable(context, info)) {
                    return adapter.loadClasses(context, classes)
                }
            }

            return null
        }

        data class ClassInfoForEvaluator(
            val containsLoops: Boolean = false,
            val containsCodeUnsupportedInEval4J: Boolean = false,
            val containsAdditionalClasses: Boolean = false
        ) {
            val isCompilingEvaluatorPreferred: Boolean
                get() = containsLoops || containsCodeUnsupportedInEval4J || containsAdditionalClasses
        }

        private fun analyzeClass(classToLoad: ClassToLoad, info: ClassInfoForEvaluator): ClassInfoForEvaluator {
            val classNode = ClassNode().apply { ClassReader(classToLoad.bytes).accept(this, 0) }

            for (method in classNode.methods) {
                if ((method.access and Opcodes.ACC_SYNCHRONIZED) != 0) {
                    return info.copy(containsCodeUnsupportedInEval4J = true)
                }
            }

            val methodToRun = classNode.methods.single { it.isEvaluationEntryPoint }

            val visitedLabels = hashSetOf<Label>()

            tailrec fun analyzeInsn(insn: AbstractInsnNode, info: ClassInfoForEvaluator): ClassInfoForEvaluator {
                when (insn) {
                    is LabelNode -> visitedLabels += insn.label
                    is JumpInsnNode -> {
                        if (insn.label.label in visitedLabels) {
                            return info.copy(containsLoops = true)
                        }
                    }
                    is TableSwitchInsnNode, is LookupSwitchInsnNode -> {
                        return info.copy(containsCodeUnsupportedInEval4J = true)
                    }
                    is InsnNode -> {
                        if (insn.opcode == Opcodes.MONITORENTER || insn.opcode == Opcodes.MONITOREXIT) {
                            return info.copy(containsCodeUnsupportedInEval4J = true)
                        }
                    }
                    is MethodInsnNode -> {
                        if (insn.opcode == Opcodes.INVOKESTATIC && insn.owner == classToLoad.className) {
                                return info.copy(containsCodeUnsupportedInEval4J = true)
                        }
                    }
                }

                val nextInsn = insn.next ?: return info
                return analyzeInsn(nextInsn, info)
            }

            val firstInsn = methodToRun.instructions?.first ?: return info
            return analyzeInsn(firstInsn, info)
        }
    }

    fun isApplicable(context: ExecutionContext, info: ClassInfoForEvaluator): Boolean

    fun loadClasses(context: ExecutionContext, classes: Collection<ClassToLoad>): ClassLoaderReference

    fun mirrorOfByteArray(bytes: ByteArray, context: ExecutionContext): ArrayReference {
        val classLoader = context.classLoader
        val arrayClass = context.findClass("byte[]", classLoader) as ArrayType
        val reference = context.newInstance(arrayClass, bytes.size)
        context.keepReference(reference)

        val mirrors = ArrayList<Value>(bytes.size)
        for (byte in bytes) {
            mirrors += context.vm.mirrorOf(byte)
        }

        var loaded = 0
        while (loaded < mirrors.size) {
            val chunkSize = min(CHUNK_SIZE, mirrors.size - loaded)
            reference.setValues(loaded, mirrors, loaded, chunkSize)
            loaded += chunkSize
        }

        return reference
    }
}
