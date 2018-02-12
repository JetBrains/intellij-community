package com.intellij.stats.events.completion

import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test

object LogEventFixtures {
    
    val sessionId = "session-id-xxx"

    val completion_started_3_items_shown = CompletionStartedEvent("", "", "", "1", sessionId, "Java", true, 1, Fixtures.lookupList, 0)

    val completion_cancelled = CompletionCancelledEvent("1", sessionId)

    val type_event_current_pos_0_left_ids_1_2 = TypeEvent("1", sessionId, listOf(1, 2), emptyList(), 0)
    val type_event_current_pos_0_left_ids_0_1 = TypeEvent("1", sessionId, listOf(0, 1), emptyList(), 0)
    val type_event_current_pos_0_left_id_0 = TypeEvent("1", sessionId, listOf(0), emptyList(), 0)

    val up_pressed_new_pos_0 = UpPressedEvent("1", sessionId, emptyList(), emptyList(), 0)
    val up_pressed_new_pos_1 = UpPressedEvent("1", sessionId, emptyList(), emptyList(), 1)
    val up_pressed_new_pos_2 = UpPressedEvent("1", sessionId, emptyList(), emptyList(), 2)

    val down_event_new_pos_0 = DownPressedEvent("1", sessionId, emptyList(), emptyList(), 0)
    val down_event_new_pos_1 = DownPressedEvent("1", sessionId, emptyList(), emptyList(), 1)
    val down_event_new_pos_2 = DownPressedEvent("1", sessionId, emptyList(), emptyList(), 2)

    val backspace_event_pos_0_left_0_1_2 = BackspaceEvent("1", sessionId, listOf(0, 1, 2), emptyList(), 0)
    val backspace_event_pos_0_left_1 = BackspaceEvent("1", sessionId, listOf(1), emptyList(), 0)

    val explicit_select_position_0 = ExplicitSelectEvent("1", sessionId, emptyList(), emptyList(), 0, 0)
    val explicit_select_position_2 = ExplicitSelectEvent("1", sessionId, emptyList(), emptyList(), 2, 2)
    val explicit_select_position_1 = ExplicitSelectEvent("1", sessionId, emptyList(), emptyList(), 1, 1)

    val selected_by_typing_0 = TypedSelectEvent("1", sessionId, 0)
    val selected_by_typing_1 = TypedSelectEvent("1", sessionId, 1)

}


class SelectedItemTest {

    lateinit var state: CompletionValidationState

    @Before
    fun setUp() {
        state = CompletionValidationState(LogEventFixtures.completion_started_3_items_shown)
    }

    @Test
    fun `explicit select`() {
        state.accept(LogEventFixtures.explicit_select_position_0)
        assertThat(state.isValid).isEqualTo(true)
    }

    @Test
    fun `explicit select of incorrect item`() {
        state.accept(LogEventFixtures.type_event_current_pos_0_left_ids_0_1)
        state.accept(LogEventFixtures.explicit_select_position_1)
        assertThat(state.isValid).isEqualTo(false)
    }

    @Test
    fun `completion cancelled`() {
        state.accept(LogEventFixtures.completion_cancelled)
        assertThat(state.isValid).isEqualTo(true)
        assertThat(state.isFinished).isEqualTo(true)
    }
}


class TypeBackspaceValidatorTest {

    lateinit var state: CompletionValidationState

    @Before
    fun setUp() {
        state = CompletionValidationState(LogEventFixtures.completion_started_3_items_shown)
    }

    @Test
    fun `type test`() {
        state.accept(LogEventFixtures.type_event_current_pos_0_left_ids_0_1)
        assertThat(state.isValid).isEqualTo(true)
        state.accept(LogEventFixtures.type_event_current_pos_0_left_id_0)
        assertThat(state.isValid).isEqualTo(true)
    }

    @Test
    fun `type completion list only narrows on typing`() {
        state.accept(LogEventFixtures.type_event_current_pos_0_left_ids_0_1)
        state.accept(LogEventFixtures.type_event_current_pos_0_left_ids_1_2)
        assertThat(state.isValid).isEqualTo(false)
    }

    @Test
    fun `can select by typing item presented in completion list`() {
        state.accept(LogEventFixtures.type_event_current_pos_0_left_ids_0_1)
        state.accept(LogEventFixtures.type_event_current_pos_0_left_id_0)
        state.accept(LogEventFixtures.selected_by_typing_1)
        assertThat(state.isValid).isEqualTo(false)
    }

    @Test
    fun `selected by typing`() {
        state.accept(LogEventFixtures.type_event_current_pos_0_left_ids_0_1)
        state.accept(LogEventFixtures.type_event_current_pos_0_left_id_0)
        state.accept(LogEventFixtures.selected_by_typing_0)
        assertThat(state.isValid).isEqualTo(true)
    }

    @Test
    fun `type and backspace`() {
        state.accept(LogEventFixtures.type_event_current_pos_0_left_ids_0_1)
        state.accept(LogEventFixtures.backspace_event_pos_0_left_0_1_2)
        assertThat(state.isValid).isEqualTo(true)
    }
}


class UpDownValidationTest {

    lateinit var state: CompletionValidationState

    @Before
    fun setUp() {
        state = CompletionValidationState(LogEventFixtures.completion_started_3_items_shown)
    }

    @Test
    fun `down pressed, new position 1, state is valid`() {
        state.accept(LogEventFixtures.down_event_new_pos_1)
        assertThat(state.isValid).isEqualTo(true)
    }

    @Test
    fun `down pressed, new pos 2, invalid`() {
        state.accept(LogEventFixtures.down_event_new_pos_2)
        assertThat(state.isValid).isEqualTo(false)
    }

    @Test
    fun `down pressed, new pos 0, invalid`() {
        state.accept(LogEventFixtures.down_event_new_pos_0)
        assertThat(state.isValid).isEqualTo(false)
    }

    @Test
    fun `down pressed, new position 0, state is not valid`() {
        state.accept(LogEventFixtures.down_event_new_pos_0)
        assertThat(state.isValid).isEqualTo(false)
    }

    @Test
    fun `sequence of downs cycles back to start`() {
        state.accept(LogEventFixtures.down_event_new_pos_1)
        state.accept(LogEventFixtures.down_event_new_pos_2)
        state.accept(LogEventFixtures.down_event_new_pos_0)
        assertThat(state.isValid).isEqualTo(true)
    }

    @Test
    fun `up pressed, new position is 2, state is valid`() {
        state.accept(LogEventFixtures.up_pressed_new_pos_2)
        assertThat(state.isValid).isEqualTo(true)
    }

    @Test
    fun `up pressed twice, new position 1, state is valid`() {
        state.accept(LogEventFixtures.up_pressed_new_pos_2)
        state.accept(LogEventFixtures.up_pressed_new_pos_1)
        assertThat(state.isValid).isEqualTo(true)
    }

    @Test
    fun `up cycles back to 0, state is valid`() {
        state.accept(LogEventFixtures.up_pressed_new_pos_2)
        state.accept(LogEventFixtures.up_pressed_new_pos_1)
        state.accept(LogEventFixtures.up_pressed_new_pos_0)
        assertThat(state.isValid).isEqualTo(true)
    }

    @Test
    fun `up pressed, new pos 1, invalid`() {
        state.accept(LogEventFixtures.up_pressed_new_pos_1)
        assertThat(state.isValid).isEqualTo(false)
    }

    @Test
    fun `up pressed, new pos 0, invalid`() {
        state.accept(LogEventFixtures.up_pressed_new_pos_0)
        assertThat(state.isValid).isEqualTo(false)
    }
    
}