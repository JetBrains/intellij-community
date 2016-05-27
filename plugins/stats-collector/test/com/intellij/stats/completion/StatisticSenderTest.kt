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

    override fun setUp() {
        super.setUp()
        
        firstFile = File("first_file")
        secondFile = File("second_file")
        
        urlProvider = mock(UrlProvider::class.java)
        requestService = mock(RequestService::class.java)
        filePathProvider = mock(FilePathProvider::class.java)
    }

    fun `test data is sent and file are removed`() {
        `when`(filePathProvider.getDataFiles()).thenReturn(listOf(firstFile, secondFile))
        val sender = StatisticSender(urlProvider, requestService, filePathProvider)
        sender.sendStatsData("uuid-secret")
        
        assertThat(firstFile.exists()).isEqualTo(false)
        assertThat(secondFile.exists()).isEqualTo(false)
    }
    
    
}