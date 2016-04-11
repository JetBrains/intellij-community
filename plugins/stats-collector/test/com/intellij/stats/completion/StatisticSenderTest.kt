package com.intellij.stats.completion

import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.UsefulTestCase
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.MockitoAnnotations
import org.picocontainer.MutablePicoContainer
import java.io.File


class StatisticsSenderTest: LightPlatformTestCase() {
    lateinit var file: File
    lateinit var tmpFile: File
    lateinit var urlProvider: UrlProvider
    lateinit var pathProvider: FilePathProvider
    lateinit var requestService: RequestService
    lateinit var pico: MutablePicoContainer
    lateinit var oldFilePathProvider: FilePathProvider
    
    var lastSendData: Map<String, String>? = null
    var lastSendDataUrl: String? = null
    
    val text = """
1 ACTION
2 ACTION
3 ACTION
"""

    @Captor
    private lateinit var captor: ArgumentCaptor<Map<String, String>>
    
    init {
        MockitoAnnotations.initMocks(this)
    }
    
    override fun setUp() {
        super.setUp()
        
        tmpFile = File("tmp.txt")
        file = File("text.txt")

        urlProvider = mock(UrlProvider::class.java)
        `when`(urlProvider.statsServerPostUrl).thenReturn("http://localhost:8080/upload")

        pathProvider = mock(FilePathProvider::class.java)
        `when`(pathProvider.statsFilePath).thenReturn(file.absolutePath)
        `when`(pathProvider.swapFile).thenReturn(tmpFile.absolutePath)

        requestService = object : RequestService() {
            override fun post(url: String, params: Map<String, String>): ResponseData? {
                lastSendData = params
                lastSendDataUrl = url
                return ResponseData(200)
            }

            override fun post(url: String, file: File): ResponseData? {
                throw UnsupportedOperationException()
            }

            override fun get(url: String): ResponseData? {
                throw UnsupportedOperationException()
            }
        }
        
        pico = ApplicationManager.getApplication().picoContainer as MutablePicoContainer
        oldFilePathProvider = pico.getComponentInstance(FilePathProvider::class.java.name) as FilePathProvider
        pico.replaceComponent(FilePathProvider::class.java, pathProvider)
        
        lastSendData = null
        lastSendDataUrl = null
    }

    override fun tearDown() {
        super.tearDown()
        if (file.exists()) {
            file.delete()
        }
        if (tmpFile.exists()) {
            tmpFile.delete()
        }
    }


    fun `test data is sent and removed`() {
        file.writeText(text)
        var logFileManager = LogFileManagerImpl()
        val sender = StatisticSender(urlProvider, logFileManager, requestService)
        sender.sendStatsData("uuid-secret-xxx")

        UsefulTestCase.assertEquals("http://localhost:8080/upload", lastSendDataUrl)
        UsefulTestCase.assertEquals("uuid-secret-xxx", lastSendData!!["uid"])
        UsefulTestCase.assertEquals(text, lastSendData!!["content"])
        
        UsefulTestCase.assertTrue(!file.exists() || file.readText().isEmpty())
        UsefulTestCase.assertTrue(!tmpFile.exists() || tmpFile.readText().isEmpty())
    }
    
    
}