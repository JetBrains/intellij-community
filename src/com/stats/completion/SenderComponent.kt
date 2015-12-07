package com.stats.completion

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ApplicationComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.updateSettings.impl.UpdateChecker
import org.apache.http.HttpStatus
import org.apache.http.client.fluent.Form
import org.apache.http.client.fluent.Request
import java.io.File
import java.io.IOException
import java.nio.file.Files


class SenderComponent(val sender: StatisticSender) : ApplicationComponent.Adapter() {
    override fun initComponent() {
        if (ApplicationManager.getApplication().isUnitTestMode) return
        ApplicationManager.getApplication().executeOnPooledThread {
            val uid = UpdateChecker.getInstallationUID(PropertiesComponent.getInstance())
            sender.sendStatsData(uid)
        }
    }
}

class StatisticSender(val urlProvider: UrlProvider, val pathProvider: FilePathProvider, val requestService: RequestService) {
    private val LOG = Logger.getInstance(StatisticSender::class.java)

    fun sendStatsData(uid: String) {
        try {
            val path = pathProvider.statsFilePath
            val file = File(path)
            if (file.exists()) {
                sendStatsFile(uid, file, onSuccess = Runnable {
                    file.delete()
                })
            }
        } catch (e: IOException) {
            LOG.error(e)
        }
    }

    private fun sendStatsFile(uid: String, file: File, onSuccess: Runnable) {
        val url = urlProvider.statsServerPostUrl
        val reader = Files.newBufferedReader(file.toPath())
        val text = reader.readText()
        reader.close()
        sendContent(url, uid, text, onSuccess)
    }

    private fun sendContent(url: String, uid: String, content: String, okAction: Runnable) {
        val map = mapOf(Pair("uid", uid), Pair("content", content))
        val data = requestService.post(url, map)
        if (data.code >= 200 && data.code < 300) {
            okAction.run()
        }
    }
    
}


abstract class RequestService {
    abstract fun post(url: String, params: Map<String, String>): ResponseData
}

class SimpleRequestService: RequestService() {

    override fun post(url: String, params: Map<String, String>): ResponseData {
        val form = Form.form()
        params.forEach {
            form.add(it.key, it.value)
        }
        val response = Request.Post(url).bodyForm(form.build()).execute()
        val httpResponse = response.returnResponse()
        return ResponseData(httpResponse.statusLine.statusCode)
    }

}


data class ResponseData(val code: Int)