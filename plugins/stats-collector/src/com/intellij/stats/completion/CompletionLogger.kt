package com.intellij.stats.completion

import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.components.ServiceManager


abstract class CompletionLoggerProvider {
    
    abstract fun newCompletionLogger(): CompletionLogger
    
    open fun dispose() = Unit
    
    companion object {
        fun getInstance(): CompletionLoggerProvider = ServiceManager.getService(CompletionLoggerProvider::class.java)
    }

}

abstract class CompletionLogger {

    abstract fun completionStarted(lookup: LookupImpl, isExperimentPerformed: Boolean, experimentVersion: Int)
    
    abstract fun afterCharTyped(c: Char, lookup: LookupImpl)
    
    abstract fun afterBackspacePressed(lookup: LookupImpl)

    abstract fun downPressed(lookup: LookupImpl)
    abstract fun upPressed(lookup: LookupImpl)

    abstract fun itemSelectedCompletionFinished(lookup: LookupImpl)
    abstract fun completionCancelled()
    abstract fun itemSelectedByTyping(lookup: LookupImpl)

    abstract fun customMessage(message: String)

}