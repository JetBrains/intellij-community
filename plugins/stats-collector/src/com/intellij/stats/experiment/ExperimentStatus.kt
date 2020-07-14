// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.stats.experiment

import com.intellij.lang.Language
import com.intellij.openapi.components.service

interface ExperimentStatus {
  companion object {
    fun getInstance(): ExperimentStatus = service()
  }

  fun forLanguage(language: Language): ExperimentInfo
  fun experimentChanged(language: Language): Boolean
}