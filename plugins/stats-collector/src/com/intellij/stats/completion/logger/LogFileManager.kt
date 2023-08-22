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

package com.intellij.stats.completion.logger

import com.intellij.stats.completion.network.assertNotEDT
import com.intellij.stats.completion.storage.FilePathProvider
import java.io.File

class LogFileManager(private val filePathProvider: FilePathProvider, private val chunkSizeLimit: Int = MAX_CHUNK_SIZE) : FileLogger {
    private companion object {
        const val MAX_CHUNK_SIZE = 30 * 1024
    }

    private var storage = LineStorage()

    override fun printLines(lines: List<String>) {
        synchronized(this) {
            for (line in lines) {
                storage.appendLine(line)
            }
            if (storage.size > chunkSizeLimit) {
                flushImpl()
            }
        }
    }

    override fun flush() {
        synchronized(this) {
            if (storage.size > 0) {
                flushImpl()
            }
        }
    }

    private fun flushImpl() {
        saveDataChunk(storage)
        filePathProvider.cleanupOldFiles()
        storage = LineStorage()
    }

    private fun saveDataChunk(storage: LineStorage) {
        assertNotEDT()
        val dir = filePathProvider.getStatsDataDirectory()
        val tmp = File(dir, "tmp_data.gz")
        storage.dump(tmp)
        tmp.renameTo(filePathProvider.getUniqueFile())
    }
}