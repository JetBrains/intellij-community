package com.intellij.stats.completion

import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.PlatformTestCase
import com.intellij.testFramework.UsefulTestCase
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.picocontainer.MutablePicoContainer
import java.io.File

fun <T> MutablePicoContainer.replaceComponent(componentInterface: Class<T>, componentInstance: T) {
    val name = componentInterface.name
    this.unregisterComponent(name)
    this.registerComponentInstance(name, componentInstance)
}

class FileLoggerTest : PlatformTestCase() {
    private lateinit var path: String
    private lateinit var oldPathProvider: FilePathProvider
    private lateinit var pico: MutablePicoContainer

    override fun setUp() {
        super.setUp()
        path = createTempFile("x.txt").absolutePath
        val mockPathProvider = mock(FilePathProvider::class.java)
        `when`(mockPathProvider.statsFilePath).thenReturn(path)

        pico = ApplicationManager.getApplication().picoContainer as MutablePicoContainer
        
        val name = FilePathProvider::class.java.name
        oldPathProvider = pico.getComponentInstance(name) as FilePathProvider
        pico.replaceComponent(FilePathProvider::class.java, mockPathProvider)
    }

    override fun tearDown() {
        pico.replaceComponent(FilePathProvider::class.java, oldPathProvider)
        val file = File(path)
        if (file.exists()) {
            file.delete()
        }
        super.tearDown()
    }

    fun `test data is appended`() {
        performLogging()

        var text = File(path).readText()
        val firstLength = text.length
        
        performLogging()
        
        text = File(path).readText()
        val secondLength = text.length
        
        UsefulTestCase.assertEquals(firstLength * 2, secondLength)
    }
    
    fun `test file is created if it doesn't exist`() {
        performLogging()
        var text = File(path).readText()
        UsefulTestCase.assertTrue(text.length > 0)
    }

    private fun performLogging(): CompletionLogger {
        val logFileManager = LogFileManagerImpl()
        var loggerProvider = CompletionFileLoggerProvider(logFileManager)
        var logger = loggerProvider.newCompletionLogger()
//        logger.completionStarted(emptyList(), false, 1)
        logger.completionCancelled()
        loggerProvider.dispose()
        return logger
    }


}