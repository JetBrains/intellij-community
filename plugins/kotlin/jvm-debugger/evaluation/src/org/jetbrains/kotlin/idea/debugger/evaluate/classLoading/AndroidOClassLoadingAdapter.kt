// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.evaluate.classLoading

import com.intellij.debugger.engine.JVMNameUtil
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.impl.DebuggerUtilsEx.mirrorOfByteArray
import com.sun.jdi.*
import org.jetbrains.kotlin.idea.debugger.base.util.DexDebugFacility
import org.jetbrains.kotlin.idea.debugger.base.util.evaluate.ExecutionContext

class AndroidOClassLoadingAdapter : AbstractAndroidClassLoadingAdapter() {
    override fun isApplicable(context: ExecutionContext, info: ClassLoadingAdapter.Companion.ClassInfoForEvaluator) = with(info) {
        isCompilingEvaluatorPreferred && DexDebugFacility.isDex(context.evaluationContext.virtualMachineProxy.virtualMachine)
    }

    private fun resolveClassLoaderClass(context: ExecutionContext): ClassType? {
        return try {
            val classLoader = context.classLoader
            tryLoadClass(context, "dalvik.system.InMemoryDexClassLoader", classLoader) as? ClassType
        } catch (e: EvaluateException) {
            null
        }
    }

    override fun loadClasses(context: ExecutionContext, classes: Collection<ClassToLoad>): ClassLoaderReference {
        val inMemoryClassLoaderClass = resolveClassLoaderClass(context) ?: error("InMemoryDexClassLoader class not found")
        val constructorMethod = inMemoryClassLoaderClass.concreteMethodByName(
            JVMNameUtil.CONSTRUCTOR_NAME, "(Ljava/nio/ByteBuffer;Ljava/lang/ClassLoader;)V"
        ) ?: error("Constructor method not found")

        val dexBytes = dex(context, classes) ?: error("Can't dex classes")
        val dexBytesMirror = mirrorOfByteArray(dexBytes, context.evaluationContext)
        val dexByteBuffer = wrapToByteBuffer(dexBytesMirror, context)

        val classLoader = context.classLoader
        val args = listOf(dexByteBuffer, classLoader)
        val newClassLoader = context.newInstance(inMemoryClassLoaderClass, constructorMethod, args) as ClassLoaderReference
        context.keepReference(newClassLoader)

        return newClassLoader
    }
}