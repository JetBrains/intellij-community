// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.evaluate.compilingEvaluator

import com.intellij.debugger.engine.evaluation.EvaluateException
import com.sun.jdi.ClassLoaderReference
import org.jetbrains.kotlin.idea.debugger.base.util.evaluate.ExecutionContext
import org.jetbrains.kotlin.idea.debugger.evaluate.LOG
import org.jetbrains.kotlin.idea.debugger.evaluate.classLoading.ClassLoadingAdapter
import org.jetbrains.kotlin.idea.debugger.evaluate.classLoading.ClassToLoad

sealed class ClassLoadingResult {
    class Success(val classLoader: ClassLoaderReference) : ClassLoadingResult()
    class Failure(val error: Throwable) : ClassLoadingResult()
    object NotNeeded : ClassLoadingResult()
}

fun loadClassesSafely(context: ExecutionContext, classes: Collection<ClassToLoad>): ClassLoadingResult {
    if (classes.isEmpty()) {
        return ClassLoadingResult.NotNeeded
    }

    return try {
        val cl = loadClasses(context, classes)
        if (cl != null) {
            ClassLoadingResult.Success(cl)
        } else {
            ClassLoadingResult.NotNeeded
        }
    } catch (e: EvaluateException) {
        throw e
    } catch (e: Throwable) {
        LOG.debug("Failed to load classes to the debug process", e)
        ClassLoadingResult.Failure(e)
    }
}

fun loadClasses(context: ExecutionContext, classes: Collection<ClassToLoad>): ClassLoaderReference? {
    return ClassLoadingAdapter.loadClasses(context, classes)
}