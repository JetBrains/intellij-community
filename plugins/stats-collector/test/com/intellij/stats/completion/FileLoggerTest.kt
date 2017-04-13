package com.intellij.stats.completion

import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.editor.Editor
import com.intellij.testFramework.PlatformTestCase
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.mockito.Matchers
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.io.File


class FileLoggerTest : PlatformTestCase() {
    private lateinit var dir: File
    private lateinit var logFile: File
    
    private lateinit var pathProvider: FilePathProvider

    override fun setUp() {
        super.setUp()
        dir = createTempDirectory()
        logFile = File(dir, "unique_1")
        
        pathProvider = mock(FilePathProvider::class.java).apply {
            `when`(getStatsDataDirectory()).thenReturn(dir)
            `when`(getUniqueFile()).thenReturn(logFile)
        }
    }

    override fun tearDown() {
        try {
            dir.deleteRecursively()
        }
        finally {
            super.tearDown()
        }
    }
    
    @Test
    fun testLogging() {
        val fileLengthBefore = logFile.length()
        
        val logFileManager = LogFileManagerImpl(pathProvider)
        val loggerProvider = CompletionFileLoggerProvider(logFileManager)
        val logger = loggerProvider.newCompletionLogger()
        
        val lookup = mock(LookupImpl::class.java).apply {
            `when`(getRelevanceObjects(Matchers.any(), Matchers.anyBoolean())).thenReturn(emptyMap())
            `when`(items).thenReturn(emptyList())
            `when`(psiFile).thenReturn(null)
            `when`(editor).thenReturn(mock(Editor::class.java))
        }
        
        logger.completionStarted(lookup, true, 2)
        
        logger.completionCancelled()
        loggerProvider.dispose()
        
        assertThat(logFile.length()).isGreaterThan(fileLengthBefore)
    }
    
}