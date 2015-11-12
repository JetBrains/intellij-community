package com.stats.completion

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.updateSettings.impl.UpdateChecker
import java.util.*


abstract class CompletionLoggerProvider {
    
    abstract fun newCompletionLogger(): CompletionLogger
    
    companion object Factory {
        fun getInstance(): CompletionLoggerProvider = ServiceManager.getService(CompletionLoggerProvider::class.java)
    } 

}

class CompletionLoggerProviderImpl : CompletionLoggerProvider() {
    override fun newCompletionLogger(): CompletionLogger {
        val installationUID = UpdateChecker.getInstallationUID(PropertiesComponent.getInstance())
        val completionUID = UUID.randomUUID().toString()
        return CompletionLoggerImpl(installationUID, completionUID)  
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

class CompletionLoggerImpl(private val installationUID: String, private val completionUID: String) : CompletionLogger() {
    
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