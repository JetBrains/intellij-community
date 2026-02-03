// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger

import com.intellij.xdebugger.frame.XValueNode
import com.jetbrains.python.PydevBundle
import org.jetbrains.annotations.Nls

class PyOnDemandValueEvaluator(linkText: @Nls String,
                               debugProcess: PyFrameAccessor,
                               var debugValue: PyDebugValue,
                               var node: XValueNode) : PyFullValueEvaluator(linkText, debugProcess, debugValue.evaluationExpression) {

  override fun startEvaluation(callback: XFullValueEvaluationCallback) {
    node.setFullValueEvaluator(PyLoadingValueEvaluator(PydevBundle.message("pydev.loading.value"), myDebugProcess, myExpression))
    callback.evaluated("... Loading Value")
    val pyAsyncValue = PyFrameAccessor.PyAsyncValue(debugValue, debugValue.createDebugValueCallback())
    myDebugProcess.loadAsyncVariablesValues(null, listOf(pyAsyncValue))
  }

  override fun isShowValuePopup(): Boolean {
    return false
  }

}