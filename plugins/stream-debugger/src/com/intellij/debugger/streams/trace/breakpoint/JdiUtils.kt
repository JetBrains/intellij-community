// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace.breakpoint

import com.intellij.debugger.engine.DebuggerUtils
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.openapi.diagnostic.logger
import com.sun.jdi.ClassNotPreparedException
import com.sun.jdi.Method
import com.sun.jdi.ReferenceType

private val LOG = logger<JdiUtils>()

object JdiUtils

internal fun EvaluationContextImpl.loadClass(className: String): ReferenceType? = debugProcess.loadClass(this, className, classLoader)

internal fun findVmMethod(evaluationContext: EvaluationContextImpl, signature: JvmMethodSignature): Method? {
  val fqClassName = signature.classFqn
  val vmClass = evaluationContext.loadClass(fqClassName)
  if (vmClass == null) {
    LOG.info("Class $fqClassName not found by jvm")
    return null
  }

  try {
    val vmMethod = vmClass.methodsByName(signature.name).findByPsiMethodSignature(signature)
      //DebuggerUtils.findMethod(vmClass, signature.name, signature.argumentTypes)
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

private fun List<Method>.findByPsiMethodSignature(signature: JvmMethodSignature): Method? = find {
  JvmMethodSignature.of(it) == signature
}

internal fun Method?.equalBySignature(other: Method): Boolean = this != null && this.name() == other.name()
                                                                && this.returnTypeName() == other.returnTypeName()
                                                                && this.argumentTypeNames() == other.argumentTypeNames()