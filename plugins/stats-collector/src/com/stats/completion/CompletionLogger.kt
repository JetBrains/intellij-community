package com.stats.completion

import com.intellij.openapi.components.ServiceManager


abstract class CompletionLoggerProvider {
    
    abstract fun newCompletionLogger(): CompletionLogger
    
    companion object Factory {
        fun getInstance(): CompletionLoggerProvider = ServiceManager.getService(CompletionLoggerProvider::class.java)
    } 

}

class CompletionLoggerProviderImpl : CompletionLoggerProvider() {
    override fun newCompletionLogger() = CompletionLoggerImpl()    
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

class CompletionLoggerImpl : CompletionLogger() {
    
    override fun completionStarted() = Unit

    override fun downPressed() = Unit

    override fun upPressed() = Unit

    override fun backspacePressed() = Unit

    override fun itemSelectedCompletionFinished() = Unit

    override fun charTyped(c: Char) = Unit

    override fun completionCancelled() = Unit

    override fun itemSelectedByTyping() = Unit
    
}