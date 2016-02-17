package com.intellij.stats.completion.experiment

import com.google.gson.Gson
import com.intellij.openapi.application.ApplicationManager
import com.intellij.stats.completion.RequestService
import com.intellij.stats.completion.UrlProvider
import com.intellij.util.Time
import java.util.*

class ABTesterHelper(private val requestSender: RequestService, private val urlProvider: UrlProvider) {
    private val gson = Gson()

    @Volatile private var lastUpdate = Date(0)
    @Volatile private var experimentInfo = ExperimentInfo()

    fun isPerformExperiment(): Boolean {
        if (isOutdated(lastUpdate)) {
            onPooledThread { updateExperimentData() }
        }
        return experimentInfo.performExperiment
    }

    fun getExperimentVersion(): Int {
        if (isOutdated(lastUpdate)) {
            onPooledThread { updateExperimentData() }
        }
        return experimentInfo.experimentVersion
    }
    
    private fun onPooledThread(block: () -> Unit) {
        val app = ApplicationManager.getApplication()
        if (app.isUnitTestMode) block() else app.executeOnPooledThread { block() }
    }
    
    protected fun updateExperimentData() {
        val response = requestSender.get(urlProvider.experimentDataUrl)
        if (response != null && response.isOK()) {
            experimentInfo = gson.fromJson(response.text, ExperimentInfo::class.java)
            lastUpdate = Date()
        }
    }

    private fun isOutdated(lastUpdate: Date): Boolean {
        return lastUpdate.time < System.currentTimeMillis() - 30 * Time.MINUTE
    }
}

class ExperimentInfo(var performExperiment: Boolean, var experimentVersion: Int) {
    constructor() : this(false, 0)
}