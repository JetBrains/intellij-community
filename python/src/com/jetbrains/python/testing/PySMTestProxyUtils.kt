// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing

import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo

fun SMTestProxy.calculateAndReturnMagnitude(): TestStateInfo.Magnitude {
  if (!isLeaf) {
    var hasSkippedChildren = false
    var hasFailedChildren = false
    var hasErrorChildren = false
    var hasPassedChildren = false
    var hasTerminated = false

    children.forEach { child ->
      val childMagnitude = child.calculateAndReturnMagnitude()
      when (childMagnitude) {
        TestStateInfo.Magnitude.PASSED_INDEX -> hasPassedChildren = true
        TestStateInfo.Magnitude.SKIPPED_INDEX, TestStateInfo.Magnitude.IGNORED_INDEX -> hasSkippedChildren = true
        TestStateInfo.Magnitude.ERROR_INDEX -> hasErrorChildren = true
        TestStateInfo.Magnitude.RUNNING_INDEX, TestStateInfo.Magnitude.TERMINATED_INDEX -> hasTerminated = true
        TestStateInfo.Magnitude.FAILED_INDEX -> hasFailedChildren = true
        else -> hasPassedChildren = true
      }
    }

    when {
      hasTerminated -> setTerminated()
      hasErrorChildren -> setTestFailed("", null, true) // No text is provided because we do not want it to be duplicated for each node
      hasFailedChildren -> setTestFailed("", null, false)
      hasSkippedChildren && !hasPassedChildren -> setTestIgnored(null, null)
      else -> setFinished()
    }
  }

  return magnitudeInfo
}