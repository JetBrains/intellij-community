// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.evaluate.classLoading

import com.sun.jdi.ClassLoaderReference
import org.jetbrains.kotlin.idea.debugger.base.util.evaluate.ExecutionContext
import org.jetbrains.org.objectweb.asm.ClassReader
import org.jetbrains.org.objectweb.asm.Label
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.tree.AbstractInsnNode
import org.jetbrains.org.objectweb.asm.tree.ClassNode
import org.jetbrains.org.objectweb.asm.tree.InsnNode
import org.jetbrains.org.objectweb.asm.tree.InvokeDynamicInsnNode
import org.jetbrains.org.objectweb.asm.tree.JumpInsnNode
import org.jetbrains.org.objectweb.asm.tree.LabelNode
import org.jetbrains.org.objectweb.asm.tree.LookupSwitchInsnNode
import org.jetbrains.org.objectweb.asm.tree.MethodInsnNode
import org.jetbrains.org.objectweb.asm.tree.TableSwitchInsnNode

interface ClassLoadingAdapter {
    companion object {
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
                    is TableSwitchInsnNode, is LookupSwitchInsnNode, is InvokeDynamicInsnNode -> {
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
}
