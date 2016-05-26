package com.intellij.stats.completion

import java.io.File

interface LogFileManager {
    fun println(message: String)
    fun dispose()
}

class AsciiMessageCharStorage {
    
    private val lines = mutableListOf<String>()
    private var size: Int = 0
    
    fun appendLine(line: String) {
        size += line.length
        lines.add(line)
    }

    fun sizeWith(newLine: String): Int = size + newLine.length
    
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

class LogFileManagerImpl(private val filePathProvider: FilePathProvider): LogFileManager {
    
    private val MAX_SIZE_BYTE = 250 * 1024
    private val storage = AsciiMessageCharStorage()

    override fun println(message: String) {
        if (storage.sizeWith(message) > MAX_SIZE_BYTE) {
            saveDataChunk(storage)
            storage.clear()
        }
        storage.appendLine(message)
    }

    override fun dispose() {
        saveDataChunk(storage)
        storage.clear()
    }

    private fun saveDataChunk(storage: AsciiMessageCharStorage) {
        val dir = filePathProvider.getStatsDataDirectory()
        val tmp = File(dir, "tmp_data")
        storage.dump(tmp)
        tmp.renameTo(filePathProvider.getUniqueFile())
    }
    
}