package com.intellij.stats.completion

import com.intellij.testFramework.LightPlatformTestCase
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.doThrow
import com.nhaarman.mockito_kotlin.mock
import org.assertj.core.api.Assertions.assertThat
import java.io.File

class TestFilePathProvider: UniqueFilesProvider("chunk", File(".")) {

    override fun cleanupOldFiles() {
        super.cleanupOldFiles()
    }

    override fun getUniqueFile(): File {
        return super.getUniqueFile()
    }

    override fun getDataFiles(): List<File> {
        return super.getDataFiles()
    }

    override fun getStatsDataDirectory(): File {
        return super.getStatsDataDirectory()
    }
}

class StatisticsSenderTest: LightPlatformTestCase() {
    lateinit var firstFile: File
    lateinit var secondFile: File
    
    val test_url = "http://xxx.com" 

    override fun setUp() {
        super.setUp()
        
        firstFile = File("first_file")
        firstFile.createNewFile()
        firstFile.writeText("text")
        
        secondFile = File("second_file")
        secondFile.createNewFile()
        secondFile.writeText("text")
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
        val filePathProvider = mock<FilePathProvider> {
            on { getDataFiles() }.doReturn(listOf(firstFile, secondFile))
        }

        val requestService = mock<RequestService> {
            on { postZipped(test_url, firstFile) }.doReturn(okResponse())
            on { postZipped(test_url, secondFile) }.doReturn(okResponse())
        }
        
        val sender = StatisticSender(requestService, filePathProvider)
        sender.sendStatsData(test_url)
        
        assertThat(firstFile.exists()).isEqualTo(false)
        assertThat(secondFile.exists()).isEqualTo(false)
    }


    fun `test removed first if only first is sent`() {
        val filePathProvider = mock<FilePathProvider> {
            on { getDataFiles() }.doReturn(listOf(firstFile, secondFile))
        }
        val requestService = mock<RequestService> {
            on { postZipped(test_url, firstFile) }.doReturn(okResponse())
            on { postZipped(test_url, secondFile) }.doReturn(failResponse())
        }
        
        val sender = StatisticSender(requestService, filePathProvider)
        sender.sendStatsData(test_url)

        assertThat(firstFile.exists()).isEqualTo(false)
        assertThat(secondFile.exists()).isEqualTo(true)
    }

    fun `test none is removed if all send failed`() {
        val filePathProvider = mock<FilePathProvider> {
            on { getDataFiles() }.doReturn(listOf(firstFile, secondFile))
        }
        
        val requestService = mock<RequestService> {
            on { postZipped(test_url, firstFile) }.doReturn(failResponse())
            on { postZipped(test_url, secondFile) }.doThrow(IllegalStateException("Should not be invoked"))
        }
        
        val sender = StatisticSender(requestService, filePathProvider)
        sender.sendStatsData(test_url)

        assertThat(firstFile.exists()).isEqualTo(true)
        assertThat(secondFile.exists()).isEqualTo(true)
    }
    
}


fun okResponse(message: String = "") = ResponseData(200, message)
fun failResponse(message: String = "") = ResponseData(404, message)