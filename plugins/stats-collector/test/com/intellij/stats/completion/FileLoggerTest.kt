package com.intellij.stats.completion

import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.editor.Editor
import com.intellij.testFramework.PlatformTestCase
import com.nhaarman.mockito_kotlin.mock
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.mockito.Matchers
import java.io.File


class FileLoggerTest : PlatformTestCase() {
    private lateinit var dir: File
    private lateinit var logFile: File
    
    private lateinit var pathProvider: FilePathProvider

    override fun setUp() {
        super.setUp()
        dir = createTempDirectory()
        logFile = File(dir, "unique_1")
        
        pathProvider = mock<FilePathProvider> {
            on { getStatsDataDirectory() }.thenReturn(dir)
            on { getUniqueFile() }.thenReturn(logFile)
        }
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
        
        val mockedEditor = mock<Editor>()
        val lookup = mock<LookupImpl> {
            on { getRelevanceObjects(Matchers.any(), Matchers.anyBoolean()) }.thenReturn(emptyMap())
            on { items }.thenReturn(emptyList())
            on { editor }.thenReturn(mockedEditor)
        }
        
        logger.completionStarted(lookup, true, 2)
        
        logger.completionCancelled()
        loggerProvider.dispose()
        
        assertThat(logFile.length()).isGreaterThan(fileLengthBefore)
    }
    
}