// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.mocks

import com.intellij.completion.ml.experiment.ExperimentInfo
import com.intellij.completion.ml.experiment.ExperimentStatus
import com.intellij.lang.Language
import com.intellij.stats.completion.network.service.RequestService
import com.intellij.stats.completion.sender.StatisticSender
import com.intellij.stats.completion.storage.UniqueFilesProvider
import org.mockito.Mockito
import java.io.File

internal class TestRequestService : RequestService() {
  companion object {
    var mock: RequestService = Mockito.mock(RequestService::class.java)
  }

  override fun postZipped(url: String, file: File) = mock.postZipped(url, file)

  override fun get(url: String) = mock.get(url)
}

internal class TestStatisticSender : StatisticSender {
  override fun sendStatsData(url: String) {
  }
}

internal class TestExperimentStatus : ExperimentStatus {
  companion object {
    private const val VERSION = 0
  }

  private var inExperiment = false
  private var shouldRank = false
  private var shouldShowArrows = false
  private var shouldCalculateFeatures = false

  override fun forLanguage(language: Language): ExperimentInfo =
    ExperimentInfo(inExperiment, VERSION, shouldRank, shouldShowArrows, shouldCalculateFeatures)

  override fun disable() = Unit

  override fun isDisabled(): Boolean = false

  fun updateExperimentSettings(inExperiment: Boolean, shouldRank: Boolean, shouldShowArrows: Boolean, shouldCalculateFeatures: Boolean) {
    this.inExperiment = inExperiment
    this.shouldRank = shouldRank
    this.shouldShowArrows = shouldShowArrows
    this.shouldCalculateFeatures = shouldCalculateFeatures
  }
}

internal class TestFilePathProvider : UniqueFilesProvider("chunk", ".", "logs-data")
