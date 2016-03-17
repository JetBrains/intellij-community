package com.intellij.stats.completion.validator

import com.intellij.stats.completion.events.LogEvent
import com.intellij.stats.completion.events.LogEventSerializer

class EventStreamValidator {

    private var previousEvent: LogEvent? = null
    
    fun isValidTransfer(s: String): Boolean {
        val event: LogEvent = LogEventSerializer.fromString(s) ?: return false
        return true    
    }
        
}

interface TransitionValidator {
    var isValid: Boolean
}

