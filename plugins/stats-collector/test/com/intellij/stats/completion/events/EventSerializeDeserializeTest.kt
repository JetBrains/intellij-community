package com.intellij.stats.completion.events

import com.intellij.testFramework.UsefulTestCase
import org.junit.Test
import java.util.*


object Fixtures {

    val userId = UUID.randomUUID().toString()

    val relevance = mapOf(Pair("sort", 1.0), Pair("proximity", 2.0))
    
    val lookupList = listOf(
            LookupEntryInfo(0, 5, relevance), 
            LookupEntryInfo(1, 9, relevance),
            LookupEntryInfo(2, 7, relevance)
    )
    
}

class EventSerializeDeserializeTest {

    private fun serializeDeserializeAndCheck(event: LogEvent) {
        val logLine = LogEventSerializer.toString(event)
        val eventFromString = LogEventSerializer.fromString(logLine)
        UsefulTestCase.assertEquals(logLine, LogEventSerializer.toString(eventFromString!!))
    }

    @Test
    fun `completion started event`() {
        var event = CompletionStartedEvent(Fixtures.userId, true, 1, Fixtures.lookupList, 0)
        serializeDeserializeAndCheck(event)
    }

    @Test
    fun `up down pressed event`() {
        var event: LogEvent = UpPressedEvent(Fixtures.userId, listOf(1, 2, 3), Fixtures.lookupList, 2)
        serializeDeserializeAndCheck(event)

        event = DownPressedEvent(Fixtures.userId, listOf(1, 2, 3), Fixtures.lookupList, 2)
        serializeDeserializeAndCheck(event)
    }
    
    @Test
    fun `up down pressed event no additional items`() {
        var event: LogEvent = UpPressedEvent(Fixtures.userId, emptyList(), emptyList(), 2)
        serializeDeserializeAndCheck(event)

        event = DownPressedEvent(Fixtures.userId, emptyList(), emptyList(), 2)
        serializeDeserializeAndCheck(event)
    }
    
    @Test
    fun `completion cancelled event`() {
        val event = CompletionCancelledEvent(Fixtures.userId)
        serializeDeserializeAndCheck(event)
    }
    
    @Test
    fun `item selected by typing event`() {
        val event = ItemSelectedByTypingEvent(Fixtures.userId, 5)
        serializeDeserializeAndCheck(event)
    }
    
    @Test
    fun `explicit select event`() {
        var event: LogEvent = ExplicitSelectEvent(Fixtures.userId, listOf(1,2,3), Fixtures.lookupList, 2)
        serializeDeserializeAndCheck(event)
        
        event = ExplicitSelectEvent(Fixtures.userId, emptyList(), emptyList(), 2)
        serializeDeserializeAndCheck(event)
    }
    
    @Test
    fun `backspace event`() {
        var event: LogEvent = BackspaceEvent(Fixtures.userId, listOf(1, 2, 3), Fixtures.lookupList, 3)
        serializeDeserializeAndCheck(event)
    }
    
    
    @Test
    fun `type event`() {
        var event = TypeEvent(Fixtures.userId, listOf(1,2,3), Fixtures.lookupList, 1)
        serializeDeserializeAndCheck(event)
    }
    
}
