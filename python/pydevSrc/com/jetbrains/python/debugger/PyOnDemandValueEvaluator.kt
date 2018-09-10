// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger

import com.intellij.xdebugger.frame.XValueNode

class PyOnDemandValueEvaluator(linkText: String,
                               debugProcess: PyFrameAccessor,
                               var debugValue: PyDebugValue,
                               var node: XValueNode) : PyFullValueEvaluator(linkText, debugProcess, debugValue.evaluationExpression) {

  override fun startEvaluation(callback: XFullValueEvaluationCallback) {
    node.setFullValueEvaluator(PyLoadingValueEvaluator("... Loading Value", myDebugProcess, myExpression))
    callback.evaluated("... Loading Value")
    val pyAsyncValue = PyFrameAccessor.PyAsyncValue<String>(debugValue, debugValue.createDebugValueCallback())
    myDebugProcess.loadAsyncVariablesValues(listOf(pyAsyncValue))
  }

  override fun isShowValuePopup(): Boolean {
    return false
  }

}