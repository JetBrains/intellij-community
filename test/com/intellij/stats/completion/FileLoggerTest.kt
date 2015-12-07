package com.intellij.stats.completion

import com.intellij.testFramework.PlatformTestCase
import com.intellij.testFramework.UsefulTestCase
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.io.File

class FileLoggerTest : PlatformTestCase() {
    private val path = "x.txt"

    override fun tearDown() {
        val file = File(path)
        if (file.exists()) {
            file.delete()
        }
        super.tearDown()
    }

    fun `test data is appended`() {
        val path = createTempFile(path).absolutePath
        val mockPathProvider = mock(FilePathProvider::class.java)
        `when`(mockPathProvider.statsFilePath).thenReturn(path)
        
        performLogging(mockPathProvider)

        var text = File(path).readText()
        val firstLength = text.length
        
        performLogging(mockPathProvider)
        
        text = File(path).readText()
        val secondLength = text.length
        
        UsefulTestCase.assertEquals(firstLength * 2, secondLength)
    }
    
    fun `test file is created if it doesn't exist`() {
        val path = path
        val mockPathProvider = mock(FilePathProvider::class.java)
        `when`(mockPathProvider.statsFilePath).thenReturn(path)
        performLogging(mockPathProvider)
        var text = File(path).readText()
        UsefulTestCase.assertTrue(text.length > 0)
    }

    private fun performLogging(pathProvider: FilePathProvider): CompletionLogger {
        var loggerProvider = CompletionFileLoggerProvider(pathProvider)
        var logger = loggerProvider.newCompletionLogger()
        logger.completionStarted(emptyList())
        logger.completionCancelled()
        loggerProvider.dispose()
        return logger
    }


}