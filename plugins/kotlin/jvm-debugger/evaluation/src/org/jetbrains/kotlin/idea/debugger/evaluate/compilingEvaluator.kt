// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.evaluate.compilingEvaluator

import com.intellij.debugger.engine.evaluation.EvaluateException
import com.sun.jdi.ClassLoaderReference
import org.jetbrains.kotlin.idea.debugger.base.util.evaluate.ExecutionContext
import org.jetbrains.kotlin.idea.debugger.evaluate.classLoading.ClassLoadingAdapter
import org.jetbrains.kotlin.idea.debugger.evaluate.classLoading.ClassToLoad

fun loadClassesSafely(context: ExecutionContext, classes: Collection<ClassToLoad>): Result<ClassLoaderReference?> {
    try {
        val classLoader = ClassLoadingAdapter.loadClasses(context, classes)
        return Result.success(classLoader)
    } catch (e: EvaluateException) {
        throw e
    } catch (e: Throwable) {
        return Result.failure(e)
    }
}