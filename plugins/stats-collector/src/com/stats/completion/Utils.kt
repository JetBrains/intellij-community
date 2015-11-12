package com.stats.completion

import com.intellij.ide.plugins.PluginManager
import java.io.File

abstract class UrlProvider {
    abstract val statsServerPostUrl: String
}

abstract class FilePathProvider {
    abstract val statsFilePath: String
}

class InternalUrlProvider: UrlProvider() {
    override val statsServerPostUrl = "localhost:8080"
}

class PluginDirectoryFilePathProvider: FilePathProvider() {
    
    override val statsFilePath: String by lazy { 
        val id = PluginManager.getPluginByClassName(CompletionLoggerProvider::class.java.name)
        val descriptor = PluginManager.getPlugin(id)
        val path = descriptor!!.path.absolutePath
        File(path, "completion_stats.txt").absolutePath
    }
    
}