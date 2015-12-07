package com.intellij.stats.completion

import com.intellij.testFramework.UsefulTestCase
import junit.framework.TestCase
import java.io.File


class StatisticsSenderTest: TestCase() {
    lateinit var file: File
    lateinit var urlProvider: UrlProvider
    lateinit var pathProvider: FilePathProvider
    lateinit var requestService: RequestService
    
    val text = """
1 ACTION
2 ACTION
3 ACTION
"""
    
    override fun setUp() {
        super.setUp()
        file = File("text.txt")
        file.writeText(text)
        
        urlProvider = object : UrlProvider() {
            override val statsServerPostUrl: String
                get() = "http://localhost:8080/upload"
        }
        
        pathProvider = object : FilePathProvider() {
            override val statsFilePath: String
                get() = file.absolutePath
        }
        
        requestService = object : RequestService() {
            override fun post(url: String, params: Map<String, String>) = ResponseData(200)
        }
    }

    override fun tearDown() {
        super.tearDown()
        if (file.exists()) {
            file.delete()
        }
    }

    fun `test data is remove when sent`() {
        val sender = StatisticSender(urlProvider, pathProvider, requestService)
        sender.sendStatsData("uuid-secret-xxx")
        UsefulTestCase.assertTrue(!file.exists())
    }
    
    
}