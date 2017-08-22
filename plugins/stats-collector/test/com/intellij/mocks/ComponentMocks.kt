package com.intellij.mocks

import com.intellij.stats.completion.RequestService
import com.intellij.stats.completion.StatisticSender
import com.intellij.stats.completion.UniqueFilesProvider
import com.intellij.stats.completion.experiment.ExperimentDecision
import com.nhaarman.mockito_kotlin.mock
import java.io.File


internal class TestExperimentDecision: ExperimentDecision {
    companion object {
        var isPerformExperiment = true
    }
    override fun isPerformExperiment(salt: String) = isPerformExperiment
}


internal class TestRequestService : RequestService() {

    companion object {
        var mock: RequestService = mock<RequestService>()
    }

    override fun post(url: String, params: Map<String, String>) = mock.post(url, params)
    override fun post(url: String, file: File) = mock.post(url, file)
    override fun postZipped(url: String, file: File) = mock.postZipped(url, file)
    override fun get(url: String) = mock.get(url)

}


internal class TestStatisticSender : StatisticSender {
    companion object {
        var sendAction: (String) -> Unit = { Unit }
    }

    override fun sendStatsData(url: String) {
    }
}


class TestFilePathProvider: UniqueFilesProvider("chunk", ".") {
    override fun cleanupOldFiles() {
        super.cleanupOldFiles()
    }

    override fun getUniqueFile(): File {
        return super.getUniqueFile()
    }

    override fun getDataFiles(): List<File> {
        return super.getDataFiles()
    }

    override fun getStatsDataDirectory(): File {
        return super.getStatsDataDirectory()
    }
}
