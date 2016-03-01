package com.intellij.stats.completion.experiment

import com.google.gson.Gson
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.updateSettings.impl.UpdateChecker
import com.intellij.stats.completion.RequestService
import com.intellij.stats.completion.UrlProvider
import com.intellij.util.Time
import org.jetbrains.annotations.TestOnly
import java.util.*

class ABTesterHelper(private val requestSender: RequestService, private val urlProvider: UrlProvider) {
    private val gson = Gson()

    @Volatile private var lastUpdate = loadLastUpdated()
    @Volatile private var experimentInfo = loadInfo()
    
    private val delay = 30 * Time.MINUTE
    
    companion object {
        private val performExperimentString = "completion.stats.collector.perform.experiment"
        private val experimentVersionString = "completion.stats.collector.perform.experiment.version"
        private val lastDayString = "completion.stats.collector.perform.last.update"
    }
    
    @TestOnly
    fun unsetLastUpdate() {
        lastUpdate = Date(0)
    }

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
        val uid = UpdateChecker.getInstallationUID(PropertiesComponent.getInstance())
        val response = requestSender.get(urlProvider.experimentDataUrl + "/$uid")
        if (response != null && response.isOK()) {
            experimentInfo = gson.fromJson(response.text, ExperimentInfo::class.java)
            lastUpdate = Date()
            saveInfo(experimentInfo)
            saveLastUpdated(lastUpdate)
        }
    }
    
    private fun isOutdated(lastUpdate: Date): Boolean {
        return lastUpdate.time < System.currentTimeMillis() - delay
    }

    private fun loadInfo(): ExperimentInfo {
        val component = PropertiesComponent.getInstance()
        val isPerformExperiment = component.getBoolean(performExperimentString, false)
        val experimentVersion = component.getInt(experimentVersionString, 0)
        return ExperimentInfo(isPerformExperiment, experimentVersion)
    }

    private fun saveInfo(info: ExperimentInfo) {
        val component = PropertiesComponent.getInstance()
        component.setValue(performExperimentString, info.performExperiment)
        component.setValue(experimentVersionString, info.experimentVersion.toString())
    }
    
    private fun saveLastUpdated(date: Date) {
        val component = PropertiesComponent.getInstance()
        component.setValue(lastDayString, date.time.toString())
    }

    private fun loadLastUpdated(): Date {
        val component = PropertiesComponent.getInstance()
        val time = component.getOrInitLong(lastDayString, 0)
        return Date(time)
    }
    
}

data class ExperimentInfo(var performExperiment: Boolean, var experimentVersion: Int) {
    constructor() : this(false, 0)
}