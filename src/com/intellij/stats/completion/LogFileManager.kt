package com.intellij.stats.completion

import java.io.File
import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.util.concurrent.locks.ReentrantLock

interface LogFileManager {
    fun println(message: String)
    fun read(): String
    fun deleteLogFile()
    fun <R> withFileLock(block: () -> R): R
    fun dispose()
}

fun FilePathProvider.getLogFile(): File {
    val path = this.statsFilePath
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
            val file = filePathProvider.getLogFile()
            writer = newFileAppender(file)
        }
        return writer!!
    }
    
    fun flush() {
        writer?.flush()
    }
    
    fun close() {
        writer?.close()
        writer = null
    }
}

class LogFileManagerImpl(private val filePathProvider: FilePathProvider): LogFileManager {
    private val lock = ReentrantLock()
    private val writer = SelfOpeningWriter(filePathProvider)

    override fun dispose() {
        writer.close()
    }

    override fun println(message: String) {
        withFileLock {
            writer.println(message)
        }
    }

    override fun read(): String {
        return withFileLock {
            writer.flush()
            val file = filePathProvider.getLogFile()
            file.readText()
        }
    }

    override fun deleteLogFile() {
        withFileLock {
            writer.close()
            val file = filePathProvider.getLogFile()
            file.delete()
            file.createNewFile()
        }
    }

    override fun <R> withFileLock(block: () -> R): R {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }
}