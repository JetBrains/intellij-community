package com.intellij.stats.completion.experiment

import com.google.gson.Gson
import com.google.gson.internal.LinkedTreeMap
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.PermanentInstallationID
import com.intellij.openapi.updateSettings.impl.UpdateChecker
import com.intellij.stats.completion.RequestService
import com.intellij.stats.completion.assertNotEDT

class StatusInfoProvider(private val requestSender: RequestService) {

    companion object {
        private val gson = Gson()

        private val statusUrl = "https://www.jetbrains.com/config/features-service-status.json"

        private val salt = "completion.stats.experiment.salt"
        private val experimentVersionString = "completion.stats.experiment.version"
    }

    @Volatile private var statusInfo = loadInfo()
    @Volatile private var serverStatus = ""
    @Volatile private var dataServerUrl = ""

    fun getExperimentVersion() = statusInfo.experimentVersion
    
    fun getDataServerUrl(): String = dataServerUrl
    
    fun isServerOk(): Boolean = serverStatus.equals("ok", ignoreCase = true)
    
    fun isPerformExperiment(): Boolean {
        val uid = PermanentInstallationID.get()
        val hash = (uid + statusInfo.salt).hashCode()
        return hash % 2 == 0
    }
    
    fun updateStatus() {
        serverStatus = ""
        dataServerUrl = ""
        
        assertNotEDT()
        val response = requestSender.get(statusUrl)
        if (response != null && response.isOK()) {
            val map = gson.fromJson(response.text, LinkedTreeMap::class.java)
            
            val salt = map["salt"]?.toString()
            val experimentVersion = map["experimentVersion"]?.toString()
            if (salt != null && experimentVersion != null) {
                //should be Int always
                val floatVersion = experimentVersion.toFloat()
                statusInfo = ExperimentInfo(floatVersion.toInt(), salt)
                saveInfo(statusInfo)
            }
            
            serverStatus = map["status"]?.toString() ?: ""
            dataServerUrl = map["urlForZipBase64Content"]?.toString() ?: ""
        }
    }

    private fun loadInfo(): ExperimentInfo {
        val component = PropertiesComponent.getInstance()
        val salt = component.getValue(salt) ?: ""
        val experimentVersion = component.getInt(experimentVersionString, 0)
        return ExperimentInfo(experimentVersion, salt)
    }

    private fun saveInfo(info: ExperimentInfo) {
        val component = PropertiesComponent.getInstance()
        component.setValue(salt, info.salt)
        component.setValue(experimentVersionString, info.experimentVersion.toString())
    }

}

data class ExperimentInfo(var experimentVersion: Int, var salt: String) {
    constructor() : this(0, "")
}