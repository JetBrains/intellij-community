// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.stats.completion

import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.stats.experiment.WebServiceStatus

/**
 * @author Vitaliy.Bibaev
 */
interface LoggingStrategy {
  fun shouldBeLogged(lookup: LookupImpl, experimentHelper: WebServiceStatus): Boolean
}

object LogAllSessions : LoggingStrategy {
  override fun shouldBeLogged(lookup: LookupImpl, experimentHelper: WebServiceStatus): Boolean = true
}

class LogEachN(private val n: Int) : LoggingStrategy {
  private var afterLastLogger = 0
  override fun shouldBeLogged(lookup: LookupImpl, experimentHelper: WebServiceStatus): Boolean {
    afterLastLogger += 1
    if (afterLastLogger == n) {
      afterLastLogger = 0
      return true
    }

    return false
  }
}