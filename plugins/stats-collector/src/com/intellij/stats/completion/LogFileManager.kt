package com.intellij.stats.completion

import java.io.File
import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.util.concurrent.locks.ReentrantLock

interface LogFileManager {
    fun println(message: String)
    fun renameLogFile(swap: File): Boolean
    fun dispose()
}

fun ensureFileCreated(path: String): File {
    val file = File(path)
    if (!file.exists()) {
        file.createNewFile()
    }
    return file
}

fun newFileAppender(file: File): PrintWriter {
    val bufferedWriter = Files.newBufferedWriter(file.toPath(), StandardOpenOption.APPEND)
    return PrintWriter(bufferedWriter)
}

class SelfOpeningWriter(private val filePathProvider: FilePathProvider) {
    private var writer: PrintWriter? = null

    fun println(message: String) {
        val writer = getWriter()
        writer.println(message)
    }
    
    private fun getWriter(): PrintWriter {
        if (writer == null) {
            val file = ensureFileCreated(filePathProvider.newUniqueFile().absolutePath)
            writer = newFileAppender(file)
        }
        return writer!!
    }
    
    fun close() {
        writer?.close()
        writer = null
    }
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
    
}

class LogFileManagerImpl(private val filePathProvider: FilePathProvider): LogFileManager {
    
    private val lock = ReentrantLock()
    
    private val MAX_SIZE_BYTE = 250 * 1024
    private val storage = AsciiMessageCharStorage()
    

    override fun println(message: String) {
        if (storage.sizeWith(message) > MAX_SIZE_BYTE) {
            scheduleSend(storage)
            storage.clear()
        }
        storage.appendLine(message)
    }

    private fun scheduleSend(storage: AsciiMessageCharStorage) {
        //save to file
        //schedule sending
    }

    override fun renameLogFile(swap: File): Boolean {
        throw UnsupportedOperationException()
    }

    override fun dispose() {
        throw UnsupportedOperationException()
    }

    private fun <R> withFileLock(block: () -> R): R {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }
}

fun main(args: Array<String>) {
}