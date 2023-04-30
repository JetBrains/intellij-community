// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.breakpoint

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.openapi.diagnostic.logger
import com.sun.jdi.ClassNotPreparedException
import com.sun.jdi.Method

private val LOG = logger<DebuggerUtils>()

/**
 * @author Shumaf Lovpache
 */
object DebuggerUtils {
  // Used to load StreamDebuggerUtils from support lib into debuggee VM
  const val STREAM_DEBUGGER_UTILS_CLASS_NAME = "com.intellij.debugger.stream.rt.StreamDebuggerUtils"
  const val STREAM_DEBUGGER_UTILS_CLASS_FILE = "com/intellij/debugger/stream/rt/StreamDebuggerUtils.class"

  fun findVmMethod(evaluationContext: EvaluationContextImpl, signature: MethodSignature): Method? {
    val fqClassName = signature.containingClass
    val vmClass = evaluationContext.loadClass(fqClassName)
    if (vmClass == null) {
      LOG.info("Class $fqClassName not found by jvm")
      return null
    }

    try {
      val vmMethod = vmClass.methodsByName(signature.name).findByPsiMethodSignature(signature)
      if (vmMethod == null) {
        LOG.info("Can not find method with signature ${signature} in $fqClassName")
        return null
      }

      return vmMethod
    }
    catch (e: ClassNotPreparedException) {
      LOG.warn("Failed to retreive $fqClassName method because class not yet been prepared.", e)
    }

    return null
  }

  internal fun Method?.equalBySignature(other: Method): Boolean = this != null && this.name() == other.name()
                                                                  && this.returnTypeName() == other.returnTypeName()
                                                                  && this.argumentTypeNames() == other.argumentTypeNames()

  private fun List<Method>.findByPsiMethodSignature(signature: MethodSignature) = this.find {
    MethodSignature.of(it) == signature
  }
}