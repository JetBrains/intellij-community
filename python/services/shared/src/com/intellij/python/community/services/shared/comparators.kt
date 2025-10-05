// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.services.shared

import com.intellij.openapi.diagnostic.fileLogger
import com.jetbrains.python.PyToolUIInfo
import com.jetbrains.python.psi.LanguageLevel
import java.util.*


private val logger = fileLogger()

object LanguageLevelComparator : Comparator<LanguageLevelHolder> {
  override fun compare(o1: LanguageLevelHolder, o2: LanguageLevelHolder): Int {
    // Backward: first python is the highest
    if (logger.isDebugEnabled) {
      logger.debug("langLevel ${o1.languageLevel} vs ${o2.languageLevel}")
    }
    return LanguageLevel.VERSION_COMPARATOR.compare(o1.languageLevel, o2.languageLevel) * -1
  }
}

object UiComparator : Comparator<UiHolder> {
  override fun compare(o1: UiHolder, o2: UiHolder): Int {
    if (logger.isDebugEnabled) {
      logger.debug("ui ${o1.ui?.toolName} vs ${o2.ui?.toolName}")
    }
    return Objects.compare(o1.ui, o2.ui, Comparator.nullsFirst(PyToolUIInfo::compareTo))
  }
}

class LanguageLevelWithUiComparator<T> : Comparator<T> where T : LanguageLevelHolder, T : UiHolder {
  override fun compare(o1: T, o2: T): Int {
    if (logger.isDebugEnabled) {
      logger.debug("full ${o1.string()} vs ${o2.string()}")
    }
    return LanguageLevelComparator.compare(o1, o2) * 10 + UiComparator.compare(o1, o2)
  }
}

private fun <T> T.string(): String where T : LanguageLevelHolder, T : UiHolder = "($languageLevel,${ui?.toolName})"
