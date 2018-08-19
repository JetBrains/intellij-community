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

package com.intellij.stats.experiment

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.internal.LinkedTreeMap
import com.intellij.ide.util.PropertiesComponent
import com.intellij.stats.network.assertNotEDT
import com.intellij.stats.network.service.RequestService


class WebServiceStatusProvider(
        private val requestSender: RequestService,
        private val experimentDecision: ExperimentDecision
): WebServiceStatus {

    companion object {
        val STATUS_URL: String = "https://www.jetbrains.com/config/features-service-status.json"

        private val GSON = Gson()

        private val SALT = "completion.stats.experiment.salt"
        private val EXPERIMENT_VERSION_KEY = "completion.stats.experiment.version"
        private val PERFORM_EXPERIMENT_KEY = "completion.ml.perform.experiment"
    }

    @Volatile private var info: ExperimentInfo = loadInfo()
    @Volatile private var serverStatus = ""
    @Volatile private var dataServerUrl = ""

    override fun experimentVersion(): Int = info.experimentVersion
    
    override fun dataServerUrl(): String = dataServerUrl

    override fun isExperimentGoingOnNow(): Boolean = info.performExperiment

    override fun isServerOk(): Boolean = serverStatus.equals("ok", ignoreCase = true)
    
    override fun isExperimentOnCurrentIDE(): Boolean {
        if (!info.performExperiment) {
            return false
        }
        return experimentDecision.isPerformExperiment(info.salt)
    }
    
    override fun updateStatus() {
        serverStatus = ""
        dataServerUrl = ""
        
        assertNotEDT()
        val response = requestSender.get(STATUS_URL)
        if (response != null && response.isOK()) {
            val map = parseServerResponse(response.text)
            
            val salt = map["salt"]?.toString()
            val experimentVersion = map["experimentVersion"]?.toString()
            val performExperiment = map["performExperiment"]?.toString() ?: "false"

            if (salt != null && experimentVersion != null) {
                //should be Int always
                val floatVersion = experimentVersion.toFloat()
                info = ExperimentInfo(floatVersion.toInt(), salt, performExperiment.toBoolean())
                saveInfo(info)
            }
            
            serverStatus = map["status"]?.toString() ?: ""
            dataServerUrl = map["urlForZipBase64Content"]?.toString() ?: ""
        }
    }

    private fun parseServerResponse(responseText: String): LinkedTreeMap<*, *> {
        try {
            return GSON.fromJson(responseText, LinkedTreeMap::class.java)
        }
        catch (e: JsonSyntaxException) {
            throw JsonSyntaxException("Expected valid JSON object, but received: $responseText", e)
        }
    }

    private fun loadInfo(): ExperimentInfo {
        return with (PropertiesComponent.getInstance()) {
            val salt = getValue(SALT, "")
            val experimentVersion = getInt(EXPERIMENT_VERSION_KEY, 0)
            val performExperiment = getBoolean(PERFORM_EXPERIMENT_KEY, false)
            ExperimentInfo(experimentVersion, salt, performExperiment)
        }
    }

    private fun saveInfo(info: ExperimentInfo) {
        with (PropertiesComponent.getInstance()) {
            setValue(SALT, info.salt)
            setValue(EXPERIMENT_VERSION_KEY, info.experimentVersion.toString())
            setValue(PERFORM_EXPERIMENT_KEY, info.performExperiment)
        }
    }

}

data class ExperimentInfo(var experimentVersion: Int, var salt: String, var performExperiment: Boolean)