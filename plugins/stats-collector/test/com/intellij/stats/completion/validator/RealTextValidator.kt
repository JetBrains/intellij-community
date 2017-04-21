package com.intellij.stats.completion.validator

import com.intellij.stats.completion.events.EventLine
import com.intellij.stats.completion.events.SessionsInputSeparator
import org.assertj.core.api.Assertions
import org.junit.Test
import java.io.*

class RealTextValidator {
    
    
    @Test
    fun test_NegativeIndexToErrChannel() {
        val file = getFile("data/completion_data.txt")
        val output = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()
        val separator = SessionsInputSeparator(FileInputStream(file), output, err)
        separator.processInput()
    }

    private fun getFile(path: String): File {
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

}


class ErrorSessionDumper(input: InputStream, output: OutputStream, error: OutputStream) : SessionsInputSeparator(input, output, error) {
    var totalFailedSessions = 0
    var totalSuccessSessions = 0
    private val dir = File("errors")

    init {
        dir.mkdir()
    }

    override fun onSessionProcessingFinished(session: List<EventLine>, isValidSession: Boolean) {
        if (!isValidSession) {
            val file = File(dir, totalFailedSessions.toString())
            if (file.exists()) {
                file.delete()
            }
            file.createNewFile()

            session.forEach {
                file.appendText(it.line)
                file.appendText("\n")
            }
            totalFailedSessions++
        }
        else {
            totalSuccessSessions++
        }
    }
}