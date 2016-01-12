package com.intellij.stats.completion

import com.intellij.openapi.components.ServiceManager


abstract class CompletionLoggerProvider {
    
    abstract fun newCompletionLogger(): CompletionLogger
    
    open fun dispose() = Unit
    
    companion object Factory {
        fun getInstance(): CompletionLoggerProvider = ServiceManager.getService(CompletionLoggerProvider::class.java)
    }

}

abstract class CompletionLogger {
    
    abstract fun completionStarted(completionList: List<LookupStringWithRelevance>)
    
    abstract fun beforeCharTyped(c: Char, completionList: List<LookupStringWithRelevance>)
    abstract fun afterCharTyped(c: Char, completionList: List<LookupStringWithRelevance>)
    
    abstract fun beforeBackspacePressed(completionList: List<LookupStringWithRelevance>)
    abstract fun afterBackspacePressed(pos: Int, itemName: String, completionList: List<LookupStringWithRelevance>)

    abstract fun downPressed(pos: Int, itemName: String, completionList: List<LookupStringWithRelevance>)
    abstract fun upPressed(pos: Int, itemName: String, completionList: List<LookupStringWithRelevance>)

    abstract fun itemSelectedCompletionFinished(pos: Int, itemName: String, completionList: List<LookupStringWithRelevance>)
    abstract fun completionCancelled()
    abstract fun itemSelectedByTyping(itemName: String)

    abstract fun customMessage(message: String)

}