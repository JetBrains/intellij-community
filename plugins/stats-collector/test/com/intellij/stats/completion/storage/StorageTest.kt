/*
 * Copyright 2000-2020 JetBrains s.r.o.
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
package com.intellij.stats.completion.storage

import com.intellij.stats.completion.logger.LineStorage
import com.intellij.stats.completion.logger.LogFileManager
import com.intellij.testFramework.HeavyPlatformTestCase
import junit.framework.TestCase
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.random.Random


class FilesProviderTest {
    private lateinit var provider: UniqueFilesProvider

    @Before
    fun setUp() {
        provider = UniqueFilesProvider("chunk", ".", "logs-data")
        provider.getStatsDataDirectory().deleteRecursively()
    }

    @After
    fun tearDown() {
        provider.getStatsDataDirectory().deleteRecursively()
    }
    
    @Test
    fun `test three new files created`() {
        provider.getUniqueFile().createNewFile()
        provider.getUniqueFile().createNewFile()
        provider.getUniqueFile().createNewFile()
        
        val createdFiles = provider.getDataFiles().count()
        
        assertThat(createdFiles).isEqualTo(3)
    }
}

class AsciiMessageStorageTest {
    private lateinit var storage: LineStorage
    private lateinit var tmpFile: File

    @Before
    fun setUp() {
        storage = LineStorage()
        tmpFile = File("tmp_test.gz")
        tmpFile.delete()
    }

    @After
    fun tearDown() {
        tmpFile.delete()
    }

    @Test
    fun `test size with new lines`() {
        val line = "text"
        storage.appendLine(line)
        val initialSize = storage.size
        storage.appendLine("one more line")
        assertThat(storage.size).isGreaterThan(initialSize)
    }

    @Test
    fun `test file content is expected`() {
        val line = "text"
        storage.appendLine(line)
        storage.appendLine(line)

        storage.dump(tmpFile)
        val lines = LineStorage.readAsLines(tmpFile)
        assertThat(lines).isEqualTo(listOf(line, line))
    }
}

private const val MAX_STORAGE_SIZE = 1024
private const val MAX_CHUNK_SIZE = 50

class FileLoggerTest : HeavyPlatformTestCase() {
    private lateinit var fileLogger: LogFileManager
    private lateinit var filesProvider: UniqueFilesProvider
    private lateinit var tempDirectory: File

    override fun setUp() {
        super.setUp()
        tempDirectory = createTempDirectory()
        filesProvider = UniqueFilesProvider("chunk", tempDirectory.absolutePath, "logs-data", MAX_STORAGE_SIZE)
        fileLogger = LogFileManager(filesProvider, MAX_CHUNK_SIZE)
    }

    override fun tearDown() {
        tempDirectory.deleteRecursively()
        super.tearDown()
    }

    fun `test chunk not empty`() {
        fileLogger.addChunk()
        val file = filesProvider.getDataFiles().single()
        assertThat(file.length()).isGreaterThan(0).withFailMessage { "Chunk must not be empty" }
    }

    fun `test single chunk`() {
        assertTrue(filesProvider.getDataFiles().isEmpty())
        fileLogger.addChunk()
        assertTrue(filesProvider.getDataFiles().isNotEmpty())
    }

    fun `test chunk size has limit`() {
        val random = Random(42)
        val iterationLimit = 10_000; // second chunk must be created after adding not more than this number of session


        for (i in 0 until iterationLimit) {
            if (filesProvider.getDataFiles().size < 2) {
                fileLogger.printLines(listOf(random.nextFloat().toString()))
            }
            else {
                break
            }
        }

        val chunks = filesProvider.getDataFiles().map { it.name }
        assertThat(chunks).hasSizeGreaterThan(1).withFailMessage { "logger has not created few chunks: $chunks" }
    }

    fun `test multiple chunks`() {
        fileLogger.addChunk()
        fileLogger.addChunk()

        val files = filesProvider.getDataFiles()
        val fileIndexes = files.mapNotNull { UniqueFilesProvider.extractChunkNumber(it.name) }
        assertThat(files.isNotEmpty()).isTrue
        assertThat(fileIndexes).isEqualTo((files.indices).toList())
    }

    fun `test delete old stuff`() {
        var minChunkNumber = 0
        var chunks = 0
        while (minChunkNumber == 0 && chunks < MAX_STORAGE_SIZE) { // chunk must be at least one byte
            fileLogger.addChunk()
            chunks += 1

            val files = filesProvider.getDataFiles()
            val totalSizeAfterCleanup = files.fold(0L) { total, file -> total + file.length() }
            assertThat(totalSizeAfterCleanup < MAX_STORAGE_SIZE).isTrue
            minChunkNumber = files.minOf { UniqueFilesProvider.extractChunkNumber(it.name)!! }
        }

        assertThat(minChunkNumber).isGreaterThan(0).withFailMessage { "chunk_0 is not removed when storage size limit exceeded" }
    }

    fun `test legacy files in storage`() {
        val oldFile = File(filesProvider.getStatsDataDirectory(), "chunk_0")
        TestCase.assertFalse(oldFile.exists())
        oldFile.writer().use { it.appendLine("Hello!") }
        TestCase.assertEquals(listOf("Hello!"), LineStorage.readAsLines(oldFile))
    }

    private fun LogFileManager.addChunk() {
        printLines(listOf("code completion session", "with multiple", "events"))
        flush()
    }
}