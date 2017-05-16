package com.intellij.stats.events.completion

import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import java.io.File

class ValidatorTest {

    lateinit var separator: SessionsFilter

    @Before
    fun setup() {
        separator = SessionsFilter()
    }

    private fun getFile(path: String): java.io.File {
        return File(javaClass.classLoader.getResource(path).file)
    }

    @Test
    fun testDataWithDeserializationErrors() {
        val file = getFile("data/validation_data")
        val separator = TestSessionSeparator()
        separator.filter(file.readLines())

        val statuses = separator.sessionsStatus
        assertThat(statuses["520198a29326"]).isFalse()
        assertThat(statuses["620198a29326"]).isTrue()
        assertThat(statuses["720198a29326"]).isFalse()
    }

}


class TestSessionSeparator : SessionsFilter() {

    val sessionsStatus = mutableMapOf<String, Boolean>()

    override fun dumpSession(session: List<EventLine>, isValidSession: Boolean) {
        val sessionUid = session.first().event!!.sessionUid
        sessionsStatus[sessionUid] = isValidSession
    }

}