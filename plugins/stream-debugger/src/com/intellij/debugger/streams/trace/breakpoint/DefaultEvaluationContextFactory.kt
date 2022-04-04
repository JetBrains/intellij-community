// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.breakpoint

import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.sun.jdi.ClassLoaderReference

class DefaultEvaluationContextFactory(private val classLoader: ClassLoaderReference) : EvaluationContextFactory {
  override fun createContext(suspendContext: SuspendContextImpl): EvaluationContextImpl {
    val currentStackFrameProxy = suspendContext.frameProxy
    return EvaluationContextImpl(suspendContext, currentStackFrameProxy)
      .withAutoLoadClasses(true)
      .also { it.classLoader = classLoader }
  }
}