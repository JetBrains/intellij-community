package com.intellij.stats.completion

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ApplicationComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.updateSettings.impl.UpdateChecker
import com.intellij.openapi.util.Disposer
import com.intellij.util.Alarm
import com.intellij.util.Time
import org.apache.http.client.fluent.Form
import org.apache.http.client.fluent.Request
import java.io.File
import java.io.IOException
import javax.swing.SwingUtilities

class SenderComponent(val sender: StatisticSender) : ApplicationComponent.Adapter() {
    private val disposable = Disposer.newDisposable()
    private val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, disposable)
    
    private fun send() {
        if (!ApplicationManager.getApplication().isUnitTestMode) {
            val uid = UpdateChecker.getInstallationUID(PropertiesComponent.getInstance())
            sender.sendStatsData(uid)
            alarm.addRequest({ send() }, Time.MINUTE)
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

class StatisticSender(val urlProvider: UrlProvider, val logFileManager: LogFileManager, val requestService: RequestService) {
    private val LOG = Logger.getInstance(StatisticSender::class.java)

    private fun prepareTextToSend(fileToSend: File): String {
        if (fileToSend.exists()) {
            val text = fileToSend.readText()
            if (text.isNotEmpty()) {
                return text
            }
            fileToSend.delete()
        }
        logFileManager.renameLogFile(fileToSend)
        return fileToSend.readText()
    }

    fun sendStatsData(uid: String) {
        assertNotEDT()
        val fileToSend = File(FilePathProvider.getInstance().swapFile)
        var textToSend = prepareTextToSend(fileToSend)
        if (textToSend.isNotEmpty()) {
            val url = urlProvider.statsServerPostUrl
            sendContent(url, uid, textToSend, onSendAction = Runnable {
                fileToSend.delete()
            })
        }
    }

    private fun assertNotEDT() {
        val isInTestMode = ApplicationManager.getApplication().isUnitTestMode
        assert(!SwingUtilities.isEventDispatchThread() || isInTestMode)
    }

    private fun sendContent(url: String, uid: String, content: String, onSendAction: Runnable) {
        val map = mapOf(Pair("uid", uid), Pair("content", content))
        val data = requestService.post(url, map)
        if (data != null && data.code >= 200 && data.code < 300) {
            onSendAction.run()
        }
    }
    
}


abstract class RequestService {
    abstract fun post(url: String, params: Map<String, String>): ResponseData?
}

class SimpleRequestService: RequestService() {
    private val LOG = Logger.getInstance(SimpleRequestService::class.java)

    override fun post(url: String, params: Map<String, String>): ResponseData? {
        val form = Form.form()
        params.forEach { form.add(it.key, it.value) }
        try {
            val response = Request.Post(url).bodyForm(form.build()).execute()
            val httpResponse = response.returnResponse()
            return ResponseData(httpResponse.statusLine.statusCode)
        } catch (e: IOException) {
            LOG.debug(e)
            return null
        }
    }

}


data class ResponseData(val code: Int)