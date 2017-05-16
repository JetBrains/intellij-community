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

import com.intellij.openapi.application.PathManager
import java.io.File
import java.io.FileFilter
import java.nio.file.Files

abstract class UrlProvider {
    abstract val statsServerPostUrl: String
    abstract val experimentDataUrl: String
}

abstract class FilePathProvider {
    abstract fun getUniqueFile(): File
    abstract fun getDataFiles(): List<File>
    abstract fun getStatsDataDirectory(): File
    abstract fun cleanupOldFiles()
}

class InternalUrlProvider: UrlProvider() {
    private val internalHost = "http://unit-617.labs.intellij.net"

    private val host: String
        get() = internalHost
    
    
    override val statsServerPostUrl = "http://test.jetstat-resty.aws.intellij.net/uploadstats"
    override val experimentDataUrl = "$host:8090/experiment/info"
}


class PluginDirectoryFilePathProvider : UniqueFilesProvider("chunk", PathManager.getSystemPath())

open class UniqueFilesProvider(private val baseName: String, 
                               private val rootDirectoryPath: String) : FilePathProvider() {
    
    private val MAX_ALLOWED_SEND_SIZE = 2 * 1024 * 1024
    
    override fun cleanupOldFiles() {
        val files = getDataFiles()
        val sizeToSend = files.fold(0L, { totalSize, file -> totalSize + file.length() })
        if (sizeToSend > MAX_ALLOWED_SEND_SIZE) {
            var currentSize = sizeToSend
            val iterator = files.iterator()
            while (iterator.hasNext() && currentSize > MAX_ALLOWED_SEND_SIZE) {
                val file = iterator.next()
                val fileSize = file.length()
                Files.delete(file.toPath())
                currentSize -= fileSize
            }
        }
    }

    override fun getUniqueFile(): File {
        val dir = getStatsDataDirectory()

        val currentMaxIndex = dir
                .listFiles(FileFilter { it.isFile })
                .filter { it.name.startsWith(baseName) }
                .map { it.name.substringAfter('_') }
                .filter { it.isIntConvertable() }
                .map(String::toInt)
                .max()
        
        val newIndex = if (currentMaxIndex != null) currentMaxIndex + 1 else 0
        
        val file = File(dir, "${baseName}_$newIndex")
        return file
    }

    override fun getDataFiles(): List<File> {
        val dir = getStatsDataDirectory()
        return dir.listFiles(FileFilter { it.isFile })
                .filter { it.name.startsWith(baseName) }
                .filter { it.name.substringAfter('_').isIntConvertable() }
                .sortedBy { it.getChunkNumber() }
    }

    override fun getStatsDataDirectory(): File {
        val dir = File(rootDirectoryPath, "completion-stats-data")
        if (!dir.exists()) {
            dir.mkdir()
        }
        return dir
    }
    
    private fun File.getChunkNumber() = this.name.substringAfter('_').toInt()

    private fun String.isIntConvertable(): Boolean {
        try {
            this.toInt()
            return true
        } catch (e: NumberFormatException) {
            return false
        }
    }
}