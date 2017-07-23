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

import java.io.File


class LineStorage {

    private val lines = mutableListOf<String>()
    var size: Int = 0
        private set
    
    fun appendLine(line: String) {
        size += line.length + System.lineSeparator().length
        lines.add(line)
    }

    fun sizeWithNewLine(newLine: String): Int = size + newLine.length + System.lineSeparator().length
    
    fun clear() {
        size = 0
        lines.clear()
    }
    
    fun dump(dest: File) {
        dest.writer().use { out -> 
            lines.forEach { out.appendln(it) } 
        }
    }
    
}

class LogFileManager(private val filePathProvider: FilePathProvider) {
    
    private val MAX_SIZE_BYTE = 250 * 1024
    private val storage = LineStorage()

    fun println(message: String) {
        if (storage.size > 0 && storage.sizeWithNewLine(message) > MAX_SIZE_BYTE) {
            saveDataChunk(storage)
            storage.clear()
        }
        storage.appendLine(message)
    }

    fun dispose() {
        if (storage.size > 0) {
            saveDataChunk(storage)
        }
        storage.clear()
    }

    private fun saveDataChunk(storage: LineStorage) {
        val dir = filePathProvider.getStatsDataDirectory()
        val tmp = File(dir, "tmp_data")
        storage.dump(tmp)
        tmp.renameTo(filePathProvider.getUniqueFile())
    }
    
}