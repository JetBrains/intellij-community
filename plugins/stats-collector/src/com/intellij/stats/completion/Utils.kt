package com.intellij.stats.completion

import com.intellij.ide.plugins.PluginManager
import java.io.File

abstract class UrlProvider {
    abstract val statsServerPostUrl: String
}

abstract class FilePathProvider {
    abstract val statsFilePath: String
}

class InternalUrlProvider: UrlProvider() {
    override val statsServerPostUrl = "http://unit-617:8080/stats/upload"
}

class PluginDirectoryFilePathProvider: FilePathProvider() {
    
    override val statsFilePath: String by lazy { 
        val id = PluginManager.getPluginByClassName(CompletionLoggerProvider::class.java.name)
        val descriptor = PluginManager.getPlugin(id)
        val path = descriptor!!.path.parentFile
        val dir = File(path, "completion-stats-data")
        if (!dir.exists()) {
            dir.mkdir()
        }
        File(dir, "completion_stats.txt").absolutePath
    }
    
}