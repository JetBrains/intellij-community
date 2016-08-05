package com.intellij.stats.completion

import com.intellij.testFramework.LightPlatformTestCase
import org.assertj.core.api.Assertions.assertThat
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.io.File

class TestFilePathProvider: UniqueFilesProvider("chunk", File("."))

class StatisticsSenderTest: LightPlatformTestCase() {
    lateinit var firstFile: File
    lateinit var secondFile: File
    
    lateinit var urlProvider: UrlProvider
    lateinit var requestService: RequestService
    lateinit var filePathProvider: FilePathProvider

    val test_url = "http://xxx.com" 

    override fun setUp() {
        super.setUp()
        
        firstFile = File("first_file")
        firstFile.createNewFile()
        firstFile.writeText("text")
        
        secondFile = File("second_file")
        secondFile.createNewFile()
        secondFile.writeText("text")
        
        urlProvider = mock(UrlProvider::class.java)
        requestService = mock(RequestService::class.java)
        filePathProvider = mock(FilePathProvider::class.java)
    }

    override fun tearDown() {
        try {
            firstFile.delete()
            secondFile.delete()
        }
        finally {
            super.tearDown()
        }
    }

    fun `test removed if every file send response was ok`() {
        `when`(filePathProvider.getDataFiles()).thenReturn(listOf(firstFile, secondFile))

        `when`(urlProvider.statsServerPostUrl).thenReturn(test_url)

        `when`(requestService.post(test_url, firstFile)).thenReturn(ResponseData(200))
        `when`(requestService.post(test_url, secondFile)).thenReturn(ResponseData(200))
        
        val sender = StatisticSender(requestService, filePathProvider)
        sender.sendStatsData("")
        
        assertThat(firstFile.exists()).isEqualTo(false)
        assertThat(secondFile.exists()).isEqualTo(false)
    }


    fun `test removed first if only first is sent`() {
        `when`(filePathProvider.getDataFiles()).thenReturn(listOf(firstFile, secondFile))

        `when`(urlProvider.statsServerPostUrl).thenReturn(test_url)

        `when`(requestService.post(test_url, firstFile)).thenReturn(ResponseData(200))
        `when`(requestService.post(test_url, secondFile)).thenReturn(ResponseData(404))

        val sender = StatisticSender(requestService, filePathProvider)
        sender.sendStatsData("")

        assertThat(firstFile.exists()).isEqualTo(false)
        assertThat(secondFile.exists()).isEqualTo(true)
    }

    fun `test none is removed if all send failed`() {
        `when`(filePathProvider.getDataFiles()).thenReturn(listOf(firstFile, secondFile))

        `when`(urlProvider.statsServerPostUrl).thenReturn(test_url)

        `when`(requestService.post(test_url, firstFile)).thenReturn(ResponseData(404))
        `when`(requestService.post(test_url, secondFile)).thenThrow(IllegalAccessError("Should not be invoked"))

        val sender = StatisticSender(requestService, filePathProvider)
        sender.sendStatsData("")

        assertThat(firstFile.exists()).isEqualTo(true)
        assertThat(secondFile.exists()).isEqualTo(true)
    }
    
}