// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.services.shared

import com.intellij.openapi.diagnostic.fileLogger
import com.jetbrains.python.PyToolUIInfo
import java.util.*


private val logger = fileLogger()

object UiComparator : Comparator<UiHolder> {
  override fun compare(o1: UiHolder, o2: UiHolder): Int {
    if (logger.isDebugEnabled) {
      logger.debug("ui ${o1.ui?.toolName} vs ${o2.ui?.toolName}")
    }
    return Objects.compare(o1.ui, o2.ui, Comparator.nullsFirst(PyToolUIInfo::compareTo))
  }
}

object PythonInfoComparator : Comparator<PythonInfoHolder> {
  override fun compare(o1: PythonInfoHolder, o2: PythonInfoHolder): Int {
    // Backward: first python is the highest
    if (logger.isDebugEnabled) {
      logger.debug("pythonInfo ${o1.pythonInfo} vs ${o2.pythonInfo}")
    }
    return o1.pythonInfo.compareTo(o2.pythonInfo)
  }
}

class PythonInfoWithUiComparator<T> : Comparator<T> where T : PythonInfoHolder, T : UiHolder {
  override fun compare(o1: T, o2: T): Int {
    if (logger.isDebugEnabled) {
      logger.debug("full ${o1.string()} vs ${o2.string()}")
    }
    val infoResult = PythonInfoComparator.compare(o1, o2)
    if (infoResult != 0) {
      return infoResult
    }
    return UiComparator.compare(o1, o2)
  }
}

private fun <T> T.string(): String where T : PythonInfoHolder, T : UiHolder =
  "(${pythonInfo.languageLevel},${ui?.toolName},free-threaded:${pythonInfo.freeThreaded})"
