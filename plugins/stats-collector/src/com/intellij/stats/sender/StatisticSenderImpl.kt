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

package com.intellij.stats.sender

import com.intellij.stats.network.service.RequestService
import com.intellij.stats.network.assertNotEDT
import com.intellij.stats.storage.FilePathProvider
import java.io.File

class StatisticSenderImpl(
        private val requestService: RequestService,
        private val filePathProvider: FilePathProvider
): StatisticSender {

    override fun sendStatsData(url: String) {
        assertNotEDT()
        val filesToSend = filePathProvider.getDataFiles()
        filesToSend.forEach {
            if (it.length() > 0) {
                val isSentSuccessfully = sendContent(url, it)
                if (isSentSuccessfully) {
                    it.delete()
                }
                else {
                    return
                }
            }
        }
    }

    private fun sendContent(url: String, file: File): Boolean {
        val data = requestService.postZipped(url, file)
        if (data != null && data.code >= 200 && data.code < 300) {
            return true
        }
        return false
    }

}