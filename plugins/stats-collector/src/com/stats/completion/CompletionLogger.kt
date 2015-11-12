package com.stats.completion

import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.updateSettings.impl.UpdateChecker
import java.io.File
import java.io.PrintWriter
import java.nio.file.Files
import java.util.*


abstract class CompletionLoggerProvider {
    
    abstract fun newCompletionLogger(): CompletionLogger
    
    open fun dispose() = Unit
    
    companion object Factory {
        fun getInstance(): CompletionLoggerProvider = ServiceManager.getService(CompletionLoggerProvider::class.java)
    }

}

class CompletionFileLoggerProvider : CompletionLoggerProvider() {
    
    val writer: PrintWriter by lazy {
        val id = PluginManager.getPluginByClassName(CompletionLoggerProvider::class.java.name)
        val descriptor = PluginManager.getPlugin(id)
        val path = descriptor!!.path.absolutePath
        val bufferedWriter = Files.newBufferedWriter(File(path, "completion_stats.txt").toPath())
        PrintWriter(bufferedWriter)
    }
    
    override fun dispose() {
        writer.close()
    }
    
    override fun newCompletionLogger(): CompletionLogger {
        val installationUID = UpdateChecker.getInstallationUID(PropertiesComponent.getInstance())
        val completionUID = UUID.randomUUID().toString()
        return CompletionFileLogger(installationUID, completionUID, writer)  
    }

}


abstract class CompletionLogger {
    
    abstract fun completionStarted()
    
    abstract fun downPressed()
    
    abstract fun upPressed()
    
    abstract fun backspacePressed()
    
    abstract fun itemSelectedCompletionFinished()
    
    abstract fun charTyped(c: Char)
    
    abstract fun completionCancelled()
    
    abstract fun itemSelectedByTyping()
    
}

class CompletionFileLogger(private val installationUID: String,
                           private val completionUID: String,
                           private val writer: PrintWriter) : CompletionLogger() {
    
    private fun println(line: String) {
        writer.println(line)
    }
    
    override fun completionStarted() {
        println("completion started")
    }

    override fun downPressed() {
        println("down pressed")
    }

    override fun upPressed() {
        println("up pressed")
    }

    override fun backspacePressed() {
        println("backspace pressed")
    }

    override fun itemSelectedCompletionFinished() {
        println("item selected completion finished")
    }

    override fun charTyped(c: Char) {
        println("char typed")
    }

    override fun completionCancelled() {
        println("completion cancelled")
    }

    override fun itemSelectedByTyping() {
        println("item selected by typing")
    }
    
}