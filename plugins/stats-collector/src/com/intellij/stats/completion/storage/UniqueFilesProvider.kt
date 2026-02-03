// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.stats.completion.storage

import com.intellij.openapi.application.PathManager
import java.io.File
import java.io.FileFilter
import java.nio.file.Files

/**
 * If you want to implement some other type of logging this is a goto class to temporarily store data locally, until it
 * will be sent to log service.
 *
 * @baseFileName, files will be named ${baseFileName}_{intIndex}
 * @rootDirectoryPath, root directory where folder named @logsDirectory will be created and all files will be stored
 * @logsDirectoryName, name of directory in root directory which will be used to store files
 */
open class UniqueFilesProvider(private val baseFileName: String,
                               private val rootDirectoryPath: String,
                               private val logsDirectoryName: String,
                               private val storageSizeLimit: Int = MAX_STORAGE_SEND_SIZE) : FilePathProvider {
    companion object {
        private const val MAX_STORAGE_SEND_SIZE = 30 * 1024 * 1024

        fun extractChunkNumber(filename: String): Int? {
            return filename.substringAfter("_").substringBefore(".gz").toIntOrNull()
        }
    }

    override fun cleanupOldFiles() {
        val files = getDataFiles()
        val storageSize = files.fold(0L) { totalSize, file -> totalSize + file.length() }
        if (storageSize > storageSizeLimit) {
            var currentSize = storageSize
            val iterator = files.iterator()
            while (iterator.hasNext() && currentSize > storageSizeLimit) {
                val file = iterator.next()
                val fileSize = file.length()
                Files.delete(file.toPath())
                currentSize -= fileSize
            }
        }
    }

    override fun getUniqueFile(): File {
        val dir = getStatsDataDirectory()

        val currentMaxIndex = listChunks().maxOfOrNull { it.number }
        val newIndex = if (currentMaxIndex != null) currentMaxIndex + 1 else 0

        return File(dir, "${baseFileName}_$newIndex.gz")
    }

    override fun getDataFiles(): List<File> {
        return listChunks().map { it.file }
    }

    override fun getStatsDataDirectory(): File {
        val dir = File(rootDirectoryPath, logsDirectoryName)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun listChunks(): List<Chunk> {
        return getStatsDataDirectory().filesOnly().mapNotNull { it.asChunk() }.sortedBy { it.number }.toList()
    }

    private fun File.filesOnly(): Sequence<File> {
        val files: Array<out File>? = this.listFiles(FileFilter { it.isFile })
        if (files == null) {
            val diagnostics = when {
                !exists() -> "file does not exist"
                !isDirectory -> "file is not a directory"
                isFile -> "file should be a directory but it is a file"
                else -> "unknown error"
            }

            throw Exception("Invalid directory path: ${this.relativeTo(File(PathManager.getSystemPath()))}. Info: $diagnostics")
        }

        return files.asSequence()
    }

    private fun File.asChunk(): Chunk? {
        if (!isFile) return null
        val filename = name
        if (!filename.startsWith(baseFileName)) return null
        val number = extractChunkNumber(filename)
        return if (number == null) null else Chunk(this, number)
    }

    private data class Chunk(val file: File, val number: Int)
}