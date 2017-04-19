package com.intellij.stats.completion.validator

import com.intellij.stats.completion.events.LogEvent
import com.intellij.stats.completion.events.LogEventSerializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream


class EventStreamValidatorTest {
    
    @Test
    fun simple_sequence_of_actions() {
        val list = listOf(LogEventFixtures.completion_started_3_items_shown, LogEventFixtures.explicit_select_position_0)
        validate(list, list.join(), "")
    }

    @Test
    fun sample_error_sequence_of_actions() {
        val list = listOf(LogEventFixtures.completion_started_3_items_shown, LogEventFixtures.explicit_select_position_1)
        validate(list, expectedOut = "", expectedErr = list.join())
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
        validate(list, list.join(), expectedErr = "")
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
        validate(list, expectedOut = "", expectedErr = list.join())
    }

    @Test
    fun selected_by_typing() {
        val list = listOf(
                LogEventFixtures.completion_started_3_items_shown,
                LogEventFixtures.type_event_current_pos_0_left_ids_1_2,
                LogEventFixtures.type_event_current_pos_0_left_id_0,
                LogEventFixtures.selected_by_typing_0
        )
        validate(list, expectedOut = "", expectedErr = list.join())
    }

    @Test
    fun selected_by_typing_error() {
        val list = listOf(
                LogEventFixtures.completion_started_3_items_shown,
                LogEventFixtures.type_event_current_pos_0_left_ids_0_1,
                LogEventFixtures.down_event_new_pos_1,
                LogEventFixtures.explicit_select_position_1
        )
        validate(list, expectedOut = list.join(), expectedErr = "")
    }

    private fun validate(list: List<LogEvent>, expectedOut: String, expectedErr: String) {
        val output = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()

        val input = list.join().toByteArray()
        val separator = SessionsInputSeparator(ByteArrayInputStream(input), output, err)
        separator.processInput()

        assertThat(err.toString().trim()).isEqualTo(expectedErr)
        assertThat(output.toString().trim()).isEqualTo(expectedOut)
    }

}

private fun List<LogEvent>.join(): String {
    return this.map { LogEventSerializer.toString(it) }.joinToString(System.lineSeparator())
}