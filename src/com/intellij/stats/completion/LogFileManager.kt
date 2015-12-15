package com.intellij.stats.completion

import java.io.File
import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.util.concurrent.locks.ReentrantLock

interface LogFileManager {
    fun println(message: String)
    fun read(): String
    fun clearLogFile()
    fun <R> withFileLock(block: () -> R): R
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

class SelfOpeningWriter {
    private var writer: PrintWriter? = null

    fun println(message: String) {
        val writer = getWriter()
        writer.println(message)
    }
    
    private fun getWriter(): PrintWriter {
        if (writer == null) {
            val file = ensureFileCreated(FilePathProvider.getInstance().statsFilePath)
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

class LogFileManagerImpl: LogFileManager {
    private val lock = ReentrantLock()
    private val writer = SelfOpeningWriter()

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
            val file = File(getLogFilePath())
            if (file.exists()) file.readText() else ""
        }
    }

    override fun clearLogFile() {
        withFileLock {
            writer.close()
            val file = File(getLogFilePath())
            if (file.exists()) {
                file.delete()
            }
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
    
    private fun getLogFilePath(): String {
        val pathProvider = FilePathProvider.getInstance()
        return pathProvider.statsFilePath
    }
}