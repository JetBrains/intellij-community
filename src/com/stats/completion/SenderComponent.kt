package com.stats.completion

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ApplicationComponent
import com.intellij.openapi.updateSettings.impl.UpdateChecker
import org.apache.http.HttpStatus
import org.apache.http.client.fluent.Form
import org.apache.http.client.fluent.Request
import java.io.File
import java.io.IOException
import java.nio.file.Files


class SenderComponent(val urlProvider: UrlProvider, val pathProvider: FilePathProvider) : ApplicationComponent.Adapter() {

    override fun initComponent() {
        if (ApplicationManager.getApplication().isUnitTestMode) return
        
        ApplicationManager.getApplication().executeOnPooledThread {
            sendStatsData()
        }
    }

    private fun sendStatsData() {
        try {
            val path = pathProvider.statsFilePath
            val file = File(path)
            if (file.exists()) {
                sendStatsFile(file, onSuccess = Runnable {
                    file.delete()
                })
            }
        } catch (e: IOException) {
            println(e)
        }
    }

    private fun sendStatsFile(file: File, onSuccess: Runnable) {
        val url = urlProvider.statsServerPostUrl
        val reader = Files.newBufferedReader(file.toPath())
        val text = reader.readText()
        reader.close()
        sendContent(url, text, onSuccess)
    }

    fun sendContent(url: String, content: String, okAction: Runnable) {
        val form = Form.form().add("content", content)
                .add("uid", UpdateChecker.getInstallationUID(PropertiesComponent.getInstance()))
                .build()

        val response = Request.Post(url).bodyForm(form).execute()
        val httpResponse = response.returnResponse()
        if (httpResponse.statusLine.statusCode == HttpStatus.SC_OK) {
            okAction.run()
        }
    }
    
}