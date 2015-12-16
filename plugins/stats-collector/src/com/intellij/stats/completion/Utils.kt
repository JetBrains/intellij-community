package com.intellij.stats.completion

import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.components.ServiceManager
import java.io.File

abstract class UrlProvider {
    abstract val statsServerPostUrl: String

    companion object {
        fun getInstance() = ServiceManager.getService(UrlProvider::class.java)
    }

}

abstract class FilePathProvider {
    abstract val swapFile: String
    abstract val statsFilePath: String

    companion object {
        fun getInstance() = ServiceManager.getService(FilePathProvider::class.java)
    }
}

class InternalUrlProvider: UrlProvider() {
    override val statsServerPostUrl = "http://unit-617:8080/stats/upload"
}

class PluginDirectoryFilePathProvider: FilePathProvider() {
    
    override val statsFilePath: String by lazy { 
        val dir = getPluginsDir()
        File(dir, "completion_stats.txt").absolutePath
    }

    override val swapFile: String by lazy {
        val dir = getPluginsDir()
        File(dir, "data_to_send.txt").absolutePath
    }

    private fun getPluginsDir(): File {
        val id = PluginManager.getPluginByClassName(CompletionLoggerProvider::class.java.name)
        val descriptor = PluginManager.getPlugin(id)
        val path = descriptor!!.path.parentFile
        val dir = File(path, "completion-stats-data")
        if (!dir.exists()) {
            dir.mkdir()
        }
        return dir
    }

}