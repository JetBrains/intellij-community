package com.intellij.stats.completion.validator

import com.intellij.stats.completion.events.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test

class CompletionStartedValidatorTest {
    
    lateinit var validator: CompletionStartedValidator

    @Before
    fun setUp() {
        val initialEvent = CompletionStartedEvent("1", true, 1, Fixtures.lookupList, 0)
        validator = CompletionStartedValidator(initialEvent)
    }

    @Test
    fun `next event - cancel`() {
        var nextEvent: LogEvent = CompletionCancelledEvent("1")
        nextEvent.accept(validator)
        assertThat(validator.isValid).isEqualTo(true)
    }
    
    @Test
    fun `next event - type`() {
        var nextEvent: LogEvent = TypeEvent("1", Fixtures.lookupList.map { it.id }, Fixtures.lookupList, 0)
        nextEvent.accept(validator)
        assertThat(validator.isValid).isEqualTo(true)
    }
    
    @Test
    fun `next event - up`() {
        var nextEvent: LogEvent = UpPressedEvent("1", emptyList(), emptyList(), 2)
        nextEvent.accept(validator)
        assertThat(validator.isValid).isEqualTo(true)
    }
    
    @Test 
    fun `next event - down`() {
        var nextEvent = DownPressedEvent("1", emptyList(), emptyList(), 1)
        nextEvent.accept(validator)
        assertThat(validator.isValid).isEqualTo(true)
    }

    @Test
    fun `next event - backspace`() {
        var nextEvent = BackspaceEvent("1", emptyList(), emptyList(), 0)
        nextEvent.accept(validator)
        assertThat(validator.isValid).isEqualTo(true)
    }
    
    @Test
    fun `next event - explicit select`() {
        var nextEvent = ExplicitSelectEvent("1", emptyList(), emptyList(), 0)
        nextEvent.accept(validator)
        assertThat(validator.isValid).isEqualTo(true)
    }
    
    @Test
    fun `next event - selected by typing`() {
        var nextEvent = ItemSelectedByTypingEvent("1", 0)
        nextEvent.accept(validator)
        assertThat(validator.isValid).isEqualTo(false)
    }
    
    
    
    
}