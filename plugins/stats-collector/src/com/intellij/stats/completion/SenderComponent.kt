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
package com.intellij.stats.completion

import com.google.common.net.HttpHeaders
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ApplicationComponent
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
import com.intellij.stats.completion.experiment.WebServiceStatus
import com.intellij.stats.completion.experiment.WebServiceStatusProvider
import com.intellij.util.Alarm
import com.intellij.util.Time
import org.apache.commons.codec.binary.Base64OutputStream
import org.apache.http.HttpResponse
import org.apache.http.client.fluent.Form
import org.apache.http.client.fluent.Request
import org.apache.http.entity.ContentType
import org.apache.http.message.BasicHeader
import org.apache.http.util.EntityUtils
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.GZIPOutputStream
import javax.swing.SwingUtilities

fun assertNotEDT() {
    val isInTestMode = ApplicationManager.getApplication().isUnitTestMode
    assert(!SwingUtilities.isEventDispatchThread() || isInTestMode)
}

class SenderComponent(val sender: StatisticSender, val statusHelper: WebServiceStatus) : ApplicationComponent {
    private val LOG = logger<SenderComponent>()
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

interface StatisticSender {
    fun sendStatsData(url: String)
}

class StatisticSenderImpl(val requestService: RequestService, val filePathProvider: FilePathProvider): StatisticSender {

    override fun sendStatsData(url: String) {
        assertNotEDT()
        filePathProvider.cleanupOldFiles()
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


abstract class RequestService {
    abstract fun post(url: String, params: Map<String, String>): ResponseData?
    abstract fun post(url: String, file: File): ResponseData?
    abstract fun postZipped(url: String, file: File): ResponseData?
    abstract fun get(url: String): ResponseData?
}

class SimpleRequestService: RequestService() {
    private val LOG = logger<SimpleRequestService>()

    override fun post(url: String, params: Map<String, String>): ResponseData? {
        val form = Form.form()
        params.forEach { form.add(it.key, it.value) }
        try {
            val response = Request.Post(url).bodyForm(form.build()).execute()
            val httpResponse = response.returnResponse()
            return ResponseData(httpResponse.status())
        } catch (e: IOException) {
            LOG.debug(e)
            return null
        }
    }

    override fun postZipped(url: String, file: File): ResponseData? {
        try {
            val zippedArray = getZippedContent(file)
            val request = Request.Post(url).bodyByteArray(zippedArray).apply {
                addHeader(BasicHeader(HttpHeaders.CONTENT_ENCODING, "gzip"))
            }
            
            val response = request.execute().returnResponse()
            return ResponseData(response.status(), response.text())
        } catch (e: IOException) {
            LOG.debug(e)
            return null
        }
    }

    private fun getZippedContent(file: File): ByteArray {
        val fileText = file.readText()
        return GzipBase64Compressor.compress(fileText)
    }

    override fun post(url: String, file: File): ResponseData? {
        try {
            val response = Request.Post(url).bodyFile(file, ContentType.TEXT_HTML).execute()
            val httpResponse = response.returnResponse()
            val text = EntityUtils.toString(httpResponse.entity)
            return ResponseData(httpResponse.statusLine.statusCode, text)
        }
        catch (e: IOException) {
            LOG.debug(e)
            return null
        }
    }

    override fun get(url: String): ResponseData? {
        try {
            var data: ResponseData? = null
            Request.Get(url).execute().handleResponse { 
                val text = EntityUtils.toString(it.entity)
                data = ResponseData(it.statusLine.statusCode, text)   
            }
            return data
        } catch (e: IOException) {
            LOG.debug(e)
            return null
        }
    }
}


data class ResponseData(val code: Int, val text: String = "") {
    fun isOK() = code in 200..299
}


object GzipBase64Compressor {
    fun compress(text: String): ByteArray {
        val outputStream = ByteArrayOutputStream()
        val base64Stream = GZIPOutputStream(Base64OutputStream(outputStream))
        base64Stream.write(text.toByteArray())
        base64Stream.close()
        return outputStream.toByteArray()   
    }
}

fun HttpResponse.text(): String = EntityUtils.toString(entity)
fun HttpResponse.status(): Int = statusLine.statusCode