// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.evaluate.classLoading

import com.sun.jdi.*
import org.jetbrains.kotlin.idea.debugger.base.util.evaluate.ExecutionContext

abstract class AbstractAndroidClassLoadingAdapter : ClassLoadingAdapter {
    protected fun dex(context: ExecutionContext, classes: Collection<ClassToLoad>): ByteArray? {
        return AndroidDexer.getInstances(context.project).single().dex(classes)
    }

    protected fun wrapToByteBuffer(bytes: ArrayReference, context: ExecutionContext): ObjectReference {
        val classLoader = context.classLoader
        val byteBufferClass = context.findClass("java.nio.ByteBuffer", classLoader) as ClassType
        val wrapMethod = byteBufferClass.concreteMethodByName("wrap", "([B)Ljava/nio/ByteBuffer;")
            ?: error("'wrap' method not found")

        return context.invokeMethod(byteBufferClass, wrapMethod, listOf(bytes)) as ObjectReference
    }

    protected fun tryLoadClass(context: ExecutionContext, fqName: String, classLoader: ClassLoaderReference?): ReferenceType? {
        return try {
            context.debugProcess.loadClass(context.evaluationContext, fqName, classLoader)
        } catch (e: Throwable) {
            null
        }
    }
}