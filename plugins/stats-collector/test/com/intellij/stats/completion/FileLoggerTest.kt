/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
import java.util.*


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

        CompletionTrackerInitializer.isEnabledInTests = true
    }

    override fun tearDown() {
        CompletionTrackerInitializer.isEnabledInTests = false
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
        val uidProvider = mock(InstallationIdProvider::class.java).apply {
            `when`(installationId()).thenReturn(UUID.randomUUID().toString())
        }

        val loggerProvider = CompletionFileLoggerProvider(pathProvider, uidProvider)
        loggerProvider.initComponent()

        val logger = loggerProvider.newCompletionLogger()
        
        val lookup = mock(LookupImpl::class.java).apply {
            `when`(getRelevanceObjects(Matchers.any(), Matchers.anyBoolean())).thenReturn(emptyMap())
            `when`(items).thenReturn(emptyList())
            `when`(psiFile).thenReturn(null)
            `when`(editor).thenReturn(mock(Editor::class.java))
        }
        
        logger.completionStarted(lookup, true, 2)
        
        logger.completionCancelled()
        loggerProvider.disposeComponent()

        assertThat(logFile.length()).isGreaterThan(fileLengthBefore)
    }
    
}