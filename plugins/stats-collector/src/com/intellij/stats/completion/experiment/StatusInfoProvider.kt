package com.intellij.stats.completion.experiment

import com.google.gson.Gson
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.PermanentInstallationID
import com.intellij.stats.completion.RequestService
import com.intellij.stats.completion.assertNotEDT

class StatusInfoProvider(private val requestSender: RequestService) {

    companion object {
        private val gson = Gson()

        private val statusUrl = "https://www.jetbrains.com/config/features-service-status.json"

        private val dataServerStatus = "completion.stats.server.status"
        private val dataServerUrl = "completion.stats.server.url"
        private val salt = "completion.stats.experiment.salt"
        private val experimentVersionString = "completion.stats.experiment.version"
    }

    @Volatile private var statusInfo = loadInfo()


    fun getExperimentVersion() = statusInfo.experimentVersion
    fun getDataServerUrl(): String = statusInfo.url
    fun isServerOk(): Boolean = statusInfo.status.equals("ok", ignoreCase = true)
    fun isPerformExperiment(): Boolean {
        val uid = PermanentInstallationID.get()
        val hash = (uid + statusInfo.salt).hashCode()
        return hash % 2 == 0
    }
    
    fun updateExperimentData() {
        assertNotEDT()
        val response = requestSender.get(statusUrl)
        if (response != null && response.isOK()) {
            statusInfo = gson.fromJson(response.text, StatusInfo::class.java)
            saveInfo(statusInfo)
        }
    }

    private fun loadInfo(): StatusInfo {
        val component = PropertiesComponent.getInstance()

        val salt = component.getValue(salt) ?: ""
        val experimentVersion = component.getInt(experimentVersionString, 0)
        val dataServerUrl = component.getValue(dataServerUrl) ?: ""
        val status = component.getValue(dataServerStatus) ?: ""

        return StatusInfo(status, dataServerUrl, experimentVersion, salt)
    }

    private fun saveInfo(info: StatusInfo) {
        val component = PropertiesComponent.getInstance()
        component.setValue(salt, info.salt)
        component.setValue(experimentVersionString, info.experimentVersion.toString())
    }

}

data class StatusInfo(var status: String, var url: String, var experimentVersion: Int, var salt: String) {
    constructor() : this("", "", 0, "")
}