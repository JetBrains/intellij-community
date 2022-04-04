// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.breakpoint

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.sun.jdi.ObjectReference

/**
 * @author Shumaf Lovpache
 * Object storage aims to protect object from garbage collection
 * by storing reference to them (or using any other method that prevents collection)
 */
interface ObjectStorage {
  /**
   * Stores a reference to the object, so it is not collected by the garbage collector.
   */
  fun keep(evaluationContext: EvaluationContextImpl, obj: ObjectReference)

  /**
   * Removes object reference from storage
   */
  fun free(evaluationContext: EvaluationContextImpl, obj: ObjectReference)

  /**
   * Removes all stored object references
   */
  fun dispose()
}