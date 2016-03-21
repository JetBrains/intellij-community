package com.intellij.stats.completion.validator

import com.intellij.stats.completion.events.LogEventSerializer
import com.intellij.stats.completion.events.LookupStateLogData
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream


class StreamValidatorTest {
    
    @Test
    fun simple_sequence_of_actions() {
        val list = listOf(LogEventFixtures.completion_started_3_items_shown, LogEventFixtures.explicit_select_0)
        validate(list, list.join(), "")
    }

    @Test
    fun sample_error_sequence_of_actions() {
        val list = listOf(LogEventFixtures.completion_started_3_items_shown, LogEventFixtures.explicit_select_3)
        validate(list, "", list.join())
    }

    private fun validate(list: List<LookupStateLogData>, expectedOut: String, expectedErr: String) {
        val output = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()

        val validator = UserSessionsValidator(ByteArrayInputStream(list.join().toByteArray()), output, err)
        validator.validate()

        assertThat(err.toString().trim()).isEqualTo(expectedErr)
        assertThat(output.toString().trim()).isEqualTo(expectedOut)
    }

}

private fun List<LookupStateLogData>.join(): String {
    return this.map {
        LogEventSerializer.toString(it)
    }.joinToString("\n")
}