package com.intellij.stats.completion.validator

import com.intellij.stats.completion.events.LogEventSerializer
import com.intellij.stats.completion.events.LookupStateLogData
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream


class StreamValidatorTest {
    
    @Test
    fun xxx() {
        val list = listOf(LogEventFixtures.completion_started_3_items_shown, LogEventFixtures.explicit_select_0)
        
        val output = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()
        
        val validator = UserSessionsValidator(list.toByteArrayInputStream(), output, err)
        validator.validate()
        
        println()
    }


}

private fun List<LookupStateLogData>.toByteArrayInputStream(): ByteArrayInputStream {
    val data = this.map {
        LogEventSerializer.toString(it)
    }.joinToString("\n")
    return ByteArrayInputStream(data.toByteArray())
}