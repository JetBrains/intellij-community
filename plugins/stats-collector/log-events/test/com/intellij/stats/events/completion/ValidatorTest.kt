package com.intellij.stats.events.completion

import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.io.*

class ValidatorTest {
    
    
    @Test
    fun test_NegativeIndexToErrChannel() {
        val file = getFile("data/completion_data.txt")
        val output = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()
        val separator = SessionsInputSeparator(FileInputStream(file), output, err)
        separator.processInput()
    }

    private fun getFile(path: String): java.io.File {
        return File(javaClass.classLoader.getResource(path).file)
    }

    @Test
    fun test0() {
        val file = getFile("data/0")
        val output = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()
        val separator = SessionsInputSeparator(FileInputStream(file), output, err)
        separator.processInput()
        
        Assertions.assertThat(err.size()).isEqualTo(0)
    }

    @Test
    fun testError0() {
        val file = getFile("data/1")
        val output = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()
        val separator = SessionsInputSeparator(FileInputStream(file), output, err)
        separator.processInput()

        Assertions.assertThat(err.size()).isEqualTo(0)
    }

    @Test
    fun testDataWithDeserializationErrors() {
        val file = getFile("data/validation_data")
        val output = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()
        val separator = TestSessionSeparator(FileInputStream(file), output, err)
        separator.processInput()

        val statuses = separator.sessionsStatus
        assertThat(statuses["36de626e4ea1"]).isFalse()
        assertThat(statuses["56de626e4ea1"]).isTrue()
        assertThat(statuses["76de626e4ea1"]).isFalse()
    }

}


class TestSessionSeparator(input: InputStream, output: OutputStream, error: OutputStream) : SessionsInputSeparator(input, output, error) {

    val sessionsStatus = mutableMapOf<String, Boolean>()

    override fun dumpSession(session: List<EventLine>, isValidSession: Boolean) {
        val sessionUid = session.first().event.sessionUid
        sessionsStatus[sessionUid] = isValidSession
    }

}