// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.stats.completion.tracker

import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.application.ApplicationManager


abstract class CompletionLoggerProvider {

    abstract fun newCompletionLogger(languageName: String): CompletionLogger

    open fun dispose(): Unit = Unit

    companion object {
        fun getInstance(): CompletionLoggerProvider = ApplicationManager.getApplication().getService(CompletionLoggerProvider::class.java)
    }

}

abstract class CompletionLogger {

    abstract fun completionStarted(lookup: LookupImpl, prefixLength: Int, isExperimentPerformed: Boolean, experimentVersion: Int,
                                   timestamp: Long)

    abstract fun afterCharTyped(c: Char, lookup: LookupImpl, prefixLength: Int, timestamp: Long)

    abstract fun afterBackspacePressed(lookup: LookupImpl, prefixLength: Int, timestamp: Long)

    abstract fun downPressed(lookup: LookupImpl, timestamp: Long)
    abstract fun upPressed(lookup: LookupImpl, timestamp: Long)

    abstract fun itemSelectedCompletionFinished(lookup: LookupImpl, completionChar: Char, performance: Map<String, Long>, timestamp: Long)
    abstract fun completionCancelled(explicitly: Boolean, performance: Map<String, Long>, timestamp: Long)
    abstract fun itemSelectedByTyping(lookup: LookupImpl, performance: Map<String, Long>, timestamp: Long)

    abstract fun customMessage(message: String, timestamp: Long)
}