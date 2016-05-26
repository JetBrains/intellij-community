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
    lateinit var secondFile: File
    lateinit var firstFile: File
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

        firstFile = File("chunk_0")
        secondFile = File("chunk_1")

        urlProvider = mock(UrlProvider::class.java)
        `when`(urlProvider.statsServerPostUrl).thenReturn("http://localhost:8080/upload")

        pathProvider = mock(FilePathProvider::class.java)
        `when`(pathProvider.getDataFiles()).thenAnswer { listOf(firstFile, secondFile) }

        requestService = mock(RequestService::class.java)
        `when`(requestService.post("x", File("x"))).thenReturn(ResponseData(200))
        
        pico = ApplicationManager.getApplication().picoContainer as MutablePicoContainer
        oldFilePathProvider = pico.getComponentInstance(FilePathProvider::class.java.name) as FilePathProvider
        pico.replaceComponent(FilePathProvider::class.java, pathProvider)
        
        lastSendData = null
        lastSendDataUrl = null
    }

    override fun tearDown() {
        super.tearDown()
        if (secondFile.exists()) {
            secondFile.delete()
        }
        if (firstFile.exists()) {
            firstFile.delete()
        }
    }


    fun `test data is sent and removed`() {
        val sender = StatisticSender(urlProvider, requestService)
        sender.sendStatsData("uuid-secret-xxx")
        
        UsefulTestCase.assertTrue(!secondFile.exists())
        UsefulTestCase.assertTrue(!firstFile.exists())
    }
    
    
}