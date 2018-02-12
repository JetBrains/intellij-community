/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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