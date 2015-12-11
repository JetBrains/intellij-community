package com.intellij.stats.completion

import java.io.File
import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.util.concurrent.locks.ReentrantLock

interface LogFileManager {
    fun println(message: String)
    fun read(): String
    fun delete()
    fun <R> withFileLock(block: () -> R): R
    fun dispose()
}

class LogFileManagerImpl(private val filePathProvider: FilePathProvider): LogFileManager {
    private val lock = ReentrantLock()
    private val file: File = initLogFile()
    private var writer = newAppendWriter()

    private fun newAppendWriter(): PrintWriter {
        val bufferedWriter = Files.newBufferedWriter(file.toPath(), StandardOpenOption.APPEND)
        return PrintWriter(bufferedWriter)
    }

    private fun initLogFile(): File {
        val path = filePathProvider.statsFilePath
        val file = File(path)
        if (!file.exists()) {
            file.createNewFile()
        }
        return file
    }

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
            file.readText()
        }
    }

    override fun delete() {
        withFileLock {
            dispose()
            file.delete()
            file.createNewFile()
            writer = newAppendWriter()
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