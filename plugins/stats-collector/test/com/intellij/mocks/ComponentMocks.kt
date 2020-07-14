// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.mocks

import com.intellij.lang.Language
import com.intellij.stats.experiment.ExperimentInfo
import com.intellij.stats.experiment.ExperimentStatus
import com.intellij.stats.network.service.RequestService
import com.intellij.stats.sender.StatisticSender
import com.intellij.stats.storage.UniqueFilesProvider
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
    private var inExperiment = false
    private var shouldRank = false
    private var shouldShowArrows = false
    private var shouldCalculateFeatures = false

    override fun forLanguage(language: Language): ExperimentInfo =
      ExperimentInfo(inExperiment, 0, shouldRank, shouldShowArrows, shouldCalculateFeatures)

    override fun experimentChanged(language: Language): Boolean = false

    fun updateExperimentSettings(inExperiment: Boolean, shouldRank: Boolean, shouldShowArrows: Boolean, shouldCalculateFeatures: Boolean) {
        this.inExperiment = inExperiment
        this.shouldRank = shouldRank
        this.shouldShowArrows = shouldShowArrows
        this.shouldCalculateFeatures = shouldCalculateFeatures
    }
}


internal class TestFilePathProvider: UniqueFilesProvider("chunk", ".", "logs-data")
