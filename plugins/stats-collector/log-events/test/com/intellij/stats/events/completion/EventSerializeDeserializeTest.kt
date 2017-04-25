package com.intellij.stats.events.completion

import junit.framework.Assert.assertEquals
import org.junit.Test
import java.util.*


object Fixtures {

    val userId = UUID.randomUUID().toString()

    val relevance = mapOf(Pair("sort", 1.0.toString()), Pair("proximity", 2.0.toString()))
    
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
        assertEquals(logLine, LogEventSerializer.toString(eventFromString!!))
    }

    @Test
    fun `completion started event`() {
        val event = CompletionStartedEvent("", "", "", Fixtures.userId, "xx", "Java", true, 1, Fixtures.lookupList, 0)
        serializeDeserializeAndCheck(event)
    }

    @Test
    fun `up down pressed event`() {
        var event: LogEvent = UpPressedEvent(Fixtures.userId, "xx", listOf(1, 2, 3), Fixtures.lookupList, 2)
        serializeDeserializeAndCheck(event)

        event = DownPressedEvent(Fixtures.userId, "xx", listOf(1, 2, 3), Fixtures.lookupList, 2)
        serializeDeserializeAndCheck(event)
    }
    
    @Test
    fun `up down pressed event no additional items`() {
        var event: LogEvent = UpPressedEvent(Fixtures.userId, "xx", emptyList(), emptyList(), 2)
        serializeDeserializeAndCheck(event)

        event = DownPressedEvent(Fixtures.userId, "xx", emptyList(), emptyList(), 2)
        serializeDeserializeAndCheck(event)
    }
    
    @Test
    fun `completion cancelled event`() {
        val event = CompletionCancelledEvent(Fixtures.userId, "xx")
        serializeDeserializeAndCheck(event)
    }
    
    @Test
    fun `item selected by typing event`() {
        val event = TypedSelectEvent(Fixtures.userId, "xx", 5)
        serializeDeserializeAndCheck(event)
    }
    
    @Test
    fun `explicit select event`() {
        var event: LogEvent = ExplicitSelectEvent(Fixtures.userId, "xx", listOf(1, 2, 3), Fixtures.lookupList, 2, 2)
        serializeDeserializeAndCheck(event)
        
        event = ExplicitSelectEvent(Fixtures.userId, "xx", emptyList(), emptyList(), 2, 2)
        serializeDeserializeAndCheck(event)
    }
    
    @Test
    fun `backspace event`() {
        val event: LogEvent = BackspaceEvent(Fixtures.userId, "xx", listOf(1, 2, 3), Fixtures.lookupList, 3)
        serializeDeserializeAndCheck(event)
    }
    
    
    @Test
    fun `type event`() {
        val event = TypeEvent(Fixtures.userId, "xx", listOf(1,2,3), Fixtures.lookupList, 1)
        serializeDeserializeAndCheck(event)
    }
    
}
