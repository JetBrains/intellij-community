// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.breakpoint

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl

class ValueManagerImpl(private val objectStorage: ObjectStorage) : ValueManager {
  private val registeredBytecodeFactories: MutableMap<String, BytecodeFactory> = mutableMapOf()

  override fun <R> watch(evaluationContext: EvaluationContextImpl, init: ValueContext.() -> R): R {
    val context = ValueContextImpl(registeredBytecodeFactories, objectStorage, evaluationContext)
    return context.init()
  }

  override fun defineClass(className: String, bytesLoader: BytecodeFactory) {
    registeredBytecodeFactories[className] = bytesLoader
  }
}