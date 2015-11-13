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


class DataSender(val urlProvider: UrlProvider, val pathProvider: FilePathProvider) : ApplicationComponent.Adapter() {

    override fun initComponent() {
        if (ApplicationManager.getApplication().isUnitTestMode) return
        
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val path = pathProvider.statsFilePath
                val file = File(path)
                if (file.exists()) {
                    sendStatsFile(path)
                }
            } catch (e: IOException) {
                println(e)
            }
        }
    }

    private fun sendStatsFile(path: String) {
        val url = urlProvider.statsServerPostUrl
        val reader = Files.newBufferedReader(File(path).toPath())
        val text = reader.readText()

        sendContent(url, text, okAction = Runnable {
            try {
                reader.close()
            } finally {
                File(path).delete()
            }
        })
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