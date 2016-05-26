package com.intellij.stats.completion

import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.components.ServiceManager
import java.io.File
import java.io.FileFilter

abstract class UrlProvider {
    abstract val statsServerPostUrl: String
    
    abstract val experimentDataUrl: String
    
    companion object {
        fun getInstance() = ServiceManager.getService(UrlProvider::class.java)
    }

}

abstract class FilePathProvider {

    abstract fun newUniqueFile(): File
    abstract fun getStatsDataDirectory(): File
    
    companion object {
        fun getInstance() = ServiceManager.getService(FilePathProvider::class.java)
    }

}

class InternalUrlProvider: UrlProvider() {
    private val localhost = "http://localhost"
    private val internalHost = "http://unit-617.labs.intellij.net"

    private val host: String
        get() = if (isPropertyExists("stats.collector.localhost.server")) localhost else internalHost
    
    
    override val statsServerPostUrl = "$host:8080/stats/upload"
    override val experimentDataUrl = "$host:8090/experiment/info"
}


class PluginDirectoryFilePathProvider() : UniqueFilesProvider("chunk", getPluginDirPath())

open class UniqueFilesProvider(private val baseName: String, private val rootDirectory: File) : FilePathProvider() {

    override fun newUniqueFile(): File {
        val dir = getStatsDataDirectory()

        val currentMaxIndex = dir
                .listFiles(FileFilter { it.isFile })
                .filter { it.name.startsWith(baseName) }
                .map { it.name.substringAfter('_') }
                .map { it.toIntOrZero() }
                .max() ?: 0

        val file = File(dir, "${baseName}_${currentMaxIndex + 1}")
        file.createNewFile()
        return file
    }

    override fun getStatsDataDirectory(): File {
        val dir = File(rootDirectory, "completion-stats-data")
        if (!dir.exists()) {
            dir.mkdir()
        }
        return dir
    }

    private fun String.toIntOrZero(): Int {
        try {
            return this.toInt()
        } catch (e: NumberFormatException) {
            return 0
        }
    }

}


fun getPluginDirPath(): File {
    val id = PluginManager.getPluginByClassName(CompletionLoggerProvider::class.java.name)
    val descriptor = PluginManager.getPlugin(id)
    return descriptor!!.path.parentFile
}

fun isPropertyExists(name: String) = System.getProperty(name) != null