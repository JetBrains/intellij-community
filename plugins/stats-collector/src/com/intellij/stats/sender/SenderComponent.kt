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

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ApplicationComponent
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
import com.intellij.stats.experiment.WebServiceStatus
import com.intellij.util.Alarm
import com.intellij.util.Time

class SenderComponent(
        private val sender: StatisticSender,
        private val statusHelper: WebServiceStatus
) : ApplicationComponent {
    private companion object {
        val LOG = logger<SenderComponent>()
    }

    private val disposable = Disposer.newDisposable()
    private val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, disposable)
    private val sendInterval = 5 * Time.MINUTE

    private fun send() {
        if (ApplicationManager.getApplication().isUnitTestMode) return

        try {
            ApplicationManager.getApplication().executeOnPooledThread {
                statusHelper.updateStatus()
                if (statusHelper.isServerOk()) {
                    val dataServerUrl = statusHelper.dataServerUrl()
                    sender.sendStatsData(dataServerUrl)
                }
            }
        } catch (e: Exception) {
            LOG.error(e.message)
        } finally {
            alarm.addRequest({ send() }, sendInterval)
        }
    }

    override fun disposeComponent() {
        Disposer.dispose(disposable)
    }

    override fun initComponent() {
        ApplicationManager.getApplication().executeOnPooledThread {
            send()
        }
    }
}