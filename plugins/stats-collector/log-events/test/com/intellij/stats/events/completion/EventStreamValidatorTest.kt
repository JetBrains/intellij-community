package com.intellij.stats.events.completion

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

private fun List<LogEvent>.serialize(): List<String> = map { LogEventSerializer.toString(it) }


class EventStreamValidatorTest {

    @Test
    fun simple_sequence_of_actions() {
        val list = listOf(LogEventFixtures.completion_started_3_items_shown, LogEventFixtures.explicit_select_position_0)
        validate(list, list.map { LogEventSerializer.toString(it) }, emptyList())
    }

    @Test
    fun sample_error_sequence_of_actions() {
        val list = listOf(LogEventFixtures.completion_started_3_items_shown, LogEventFixtures.explicit_select_position_1)
        validate(list, expectedOut = emptyList(), expectedErr = list.serialize())
    }

    @Test
    fun up_down_actions() {
        val list = listOf(
          LogEventFixtures.completion_started_3_items_shown,
          LogEventFixtures.down_event_new_pos_1,
          LogEventFixtures.up_pressed_new_pos_0,
          LogEventFixtures.up_pressed_new_pos_2,
          LogEventFixtures.up_pressed_new_pos_1,
          LogEventFixtures.explicit_select_position_1
        )
        validate(list, list.serialize(), expectedErr = emptyList())
    }

    @Test
    fun up_down_actions_wrong() {
        val list = listOf(
          LogEventFixtures.completion_started_3_items_shown,
          LogEventFixtures.down_event_new_pos_1,
          LogEventFixtures.up_pressed_new_pos_0,
          LogEventFixtures.up_pressed_new_pos_2,
          LogEventFixtures.up_pressed_new_pos_1,
          LogEventFixtures.explicit_select_position_0
        )
        validate(list, expectedOut = emptyList(), expectedErr = list.serialize())
    }

    @Test
    fun selected_by_typing() {
        val list = listOf(
          LogEventFixtures.completion_started_3_items_shown,
          LogEventFixtures.type_event_current_pos_0_left_ids_1_2,
          LogEventFixtures.type_event_current_pos_0_left_id_0,
          LogEventFixtures.selected_by_typing_0
        )
        validate(list, expectedOut = emptyList(), expectedErr = list.serialize())
    }

    @Test
    fun selected_by_typing_error() {
        val list = listOf(
          LogEventFixtures.completion_started_3_items_shown,
          LogEventFixtures.type_event_current_pos_0_left_ids_0_1,
          LogEventFixtures.down_event_new_pos_1,
          LogEventFixtures.explicit_select_position_1
        )
        validate(list, expectedOut = list.serialize(), expectedErr = emptyList())
    }

    private fun validate(list: List<LogEvent>,
                         expectedOut: List<String>,
                         expectedErr: List<String>) {
        val input: List<String> = list.map { LogEventSerializer.toString(it) }
        val result = SimpleSessionValidationResult()
        val separator = InputSessionValidator(result)
        separator.filter(input)

        assertThat(result.errorLines).isEqualTo(expectedErr)
        assertThat(result.validLines).isEqualTo(expectedOut)
    }

}