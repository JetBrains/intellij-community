// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.breakpoint

import com.intellij.Patches
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.impl.DebuggerUtilsImpl
import com.sun.jdi.ObjectReference

class DisableCollectionObjectStorage : ObjectStorage {
  private val registeredObjects: MutableSet<ObjectReference> = mutableSetOf()

  override fun keep(evaluationContext: EvaluationContextImpl, obj: ObjectReference) {
    if (!Patches.IBM_JDK_DISABLE_COLLECTION_BUG) {
      DebuggerUtilsImpl.disableCollection(obj)
    }
    registeredObjects.add(obj)
  }

  override fun free(evaluationContext: EvaluationContextImpl, obj: ObjectReference) {
    if (!Patches.IBM_JDK_DISABLE_COLLECTION_BUG) {
      DebuggerUtilsImpl.enableCollection(obj)
    }
    registeredObjects.remove(obj)
  }

  override fun dispose() {
    for (obj in registeredObjects) {
      obj.enableCollection()
    }
    registeredObjects.clear()
  }
}