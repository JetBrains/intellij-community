package com.intellij.stats.completion

import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.testFramework.PlatformTestCase
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.mockito.Matchers
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
    private lateinit var dir: File
    private lateinit var logFile: File
    
    private lateinit var pathProvider: FilePathProvider

    override fun setUp() {
        super.setUp()
        dir = createTempDirectory()
        logFile = File(dir, "unique_1")
        
        pathProvider = mock(FilePathProvider::class.java)
        `when`(pathProvider.getStatsDataDirectory()).thenReturn(dir)
        `when`(pathProvider.getUniqueFile()).thenReturn(logFile)
    }

    override fun tearDown() {
        dir.deleteRecursively()
        super.tearDown()
    }
    
    
    @Test
    fun testLogging() {
        val fileLengthBefore = logFile.length()
        
        val logFileManager = LogFileManagerImpl(pathProvider)
        val loggerProvider = CompletionFileLoggerProvider(logFileManager)
        val logger = loggerProvider.newCompletionLogger()
        val lookup = mock(LookupImpl::class.java)
        
        `when`(lookup.getRelevanceObjects(Matchers.any(), Matchers.anyBoolean())).thenReturn(emptyMap())
        `when`(lookup.items).thenReturn(emptyList())
        logger.completionStarted(lookup, true, 2)
        
        logger.completionCancelled()
        loggerProvider.dispose()
        
        assertThat(logFile.length()).isGreaterThan(fileLengthBefore)
    }


}