package com.intellij.stats.events.completion

import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import java.io.File

class ValidatorTest {

    lateinit var separator: InputSessionValidator
    val sessionStatuses = hashMapOf<String, Boolean>()

    @Before
    fun setup() {
        sessionStatuses.clear()
        val result = object : SessionValidationResult {
            override fun addErrorSession(errorSession: List<EventLine>) {
                val sessionUid = errorSession.first().sessionUid ?: return
                sessionStatuses[sessionUid] = false
            }

            override fun addValidSession(validSession: List<EventLine>) {
                val sessionUid = validSession.first().sessionUid ?: return
                sessionStatuses[sessionUid] = true
            }
        }
        separator = InputSessionValidator(result)
    }

    private fun file(path: String): File {
        return File(javaClass.classLoader.getResource(path).file)
    }

    @Test
    fun testDataWithDeserializationErrors() {
        val file = file("data/validation_data")
        separator.filter(file.readLines())

        assertThat(sessionStatuses["520198a29326"]).isFalse()
        assertThat(sessionStatuses["620198a29326"]).isTrue()
        assertThat(sessionStatuses["720198a29326"]).isFalse()
    }

}