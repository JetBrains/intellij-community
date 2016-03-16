package com.intellij.stats.completion.events

import com.intellij.testFramework.UsefulTestCase
import org.junit.Test
import java.util.*


object Fixtures {
    
    val relevance = mapOf(Pair("sort", 1.0), Pair("proximity", 2.0))
    
    val lookupList = listOf(
            LookupEntryInfo(0, 5, relevance), 
            LookupEntryInfo(1, 9, relevance),
            LookupEntryInfo(2, 7, relevance)
    )
    
}

class EventSerializerTest {
    
    
    @Test
    fun `completion started event`() {
        var event = CompletionStartedEvent(UUID.randomUUID().toString(), true, 1, Fixtures.lookupList, 0)
        val logLine = LogEventSerializer.toString(event)
        val eventFromString = LogEventSerializer.fromString(logLine)
        UsefulTestCase.assertEquals(logLine, LogEventSerializer.toString(eventFromString!!))
    }
    
    
    
    
    
    
    
}
