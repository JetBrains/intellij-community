// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.stats.experiment

import com.intellij.lang.Language
import org.jetbrains.annotations.ApiStatus

@Deprecated("Left for compatibility. Will be removed in future release.")
@ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
class EmulatedExperiment {
  companion object {
    fun shouldRank(language: Language, experimentVersion: Int): Boolean = false
    fun isInsideExperiment(experimentVersion: Int): Boolean = false
  }

  fun emulate(experimentVersion: Int, performExperiment: Boolean, salt: String): Int? = null
}
