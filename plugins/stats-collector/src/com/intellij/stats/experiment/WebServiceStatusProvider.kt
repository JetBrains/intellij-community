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
import java.util.concurrent.TimeUnit


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
        private val STATUS_UPDATED_TIMESTAMP_KEY = "completion.stats.status.updated.ts"

        private val INFO_TTL = TimeUnit.DAYS.toMillis(7)
        private val DEFAULT_INFO = ExperimentInfo(0, "", false)
        private val EMULATED_EXPERIMENT = EmulatedExperiment()
    }

    @Volatile
    private var info: ExperimentInfo = loadInfoIfActual()
    @Volatile private var serverStatus = ""
    @Volatile private var dataServerUrl = ""

    override fun experimentVersion(): Int = info.experimentVersion
    
    override fun dataServerUrl(): String = dataServerUrl

    override fun isExperimentGoingOnNow(): Boolean = info.performExperiment

    override fun isServerOk(): Boolean = serverStatus.equals("ok", ignoreCase = true)
    
    override fun isExperimentOnCurrentIDE(): Boolean {
        return info.performExperiment
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
                val intVersion = experimentVersion.toFloat().toInt()
                val perform = performExperiment.toBoolean()
                val emulatedValues = EMULATED_EXPERIMENT.emulate(intVersion, perform, salt)
                info = if (emulatedValues != null) {
                    ExperimentInfo(emulatedValues.first, salt, emulatedValues.second)
                }
                else {
                    ExperimentInfo(intVersion, salt, perform)
                }
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

    private fun loadInfoIfActual(): ExperimentInfo {
        val updatedTimestamp = PropertiesComponent.getInstance().getOrInitLong(STATUS_UPDATED_TIMESTAMP_KEY, 0)
        if (updatedTimestamp != 0L && System.currentTimeMillis() - updatedTimestamp < INFO_TTL) {
            return loadInfo() ?: DEFAULT_INFO
        }

        return DEFAULT_INFO
    }

    private fun loadInfo(): ExperimentInfo? {
        val properties = PropertiesComponent.getInstance()
        val salt = properties.getValue(SALT)
        val experimentVersion = properties.getInt(EXPERIMENT_VERSION_KEY, -1)
        val performExperiment = if (properties.isValueSet(PERFORM_EXPERIMENT_KEY)) properties.isTrueValue(PERFORM_EXPERIMENT_KEY) else null
        if (salt != null && experimentVersion != -1 && performExperiment != null) {
            return ExperimentInfo(experimentVersion, salt, performExperiment)
        }

        return null
    }

    private fun saveInfo(info: ExperimentInfo) {
        with(PropertiesComponent.getInstance()) {
            setValue(SALT, info.salt)
            setValue(EXPERIMENT_VERSION_KEY, info.experimentVersion.toString())
            setValue(PERFORM_EXPERIMENT_KEY, info.performExperiment)
            setValue(STATUS_UPDATED_TIMESTAMP_KEY, System.currentTimeMillis().toString())
        }
    }

}

data class ExperimentInfo(val experimentVersion: Int, val salt: String, val performExperiment: Boolean)