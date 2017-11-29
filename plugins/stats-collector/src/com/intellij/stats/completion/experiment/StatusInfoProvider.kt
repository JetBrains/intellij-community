/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.stats.completion.experiment

import com.google.gson.Gson
import com.google.gson.internal.LinkedTreeMap
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.PermanentInstallationID
import com.intellij.stats.completion.RequestService
import com.intellij.stats.completion.assertNotEDT

interface WebServiceStatusProvider {
    fun updateStatus()

    fun getDataServerUrl(): String
    fun getExperimentVersion(): Int
    fun isServerOk(): Boolean
    fun getSalt(): String
}


fun WebServiceStatusProvider.isPerformExperiment(): Boolean {
    val uid = PermanentInstallationID.get()
    val hash = (uid + getSalt()).hashCode()
    return hash % 2 == 0
}


class StatusInfoProvider(private val requestSender: RequestService): WebServiceStatusProvider {

    companion object {
        private val gson = Gson()

        private val statusUrl = "https://www.jetbrains.com/config/features-service-status.json"

        private val salt = "completion.stats.experiment.salt"
        private val experimentVersionString = "completion.stats.experiment.version"
    }

    @Volatile private var statusInfo = loadInfo()
    @Volatile private var serverStatus = ""
    @Volatile private var dataServerUrl = ""

    override fun getExperimentVersion() = statusInfo.experimentVersion
    
    override fun getDataServerUrl(): String = dataServerUrl
    
    override fun isServerOk(): Boolean = serverStatus.equals("ok", ignoreCase = true)

    override fun getSalt() = statusInfo.salt

    override fun updateStatus() {
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

data class ExperimentInfo(var experimentVersion: Int, var salt: String)