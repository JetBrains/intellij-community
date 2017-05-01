package com.intellij.stats.events.completion

import org.assertj.core.api.Assertions
import org.junit.Test

class RealTextValidator {
    
    
    @Test
    fun test_NegativeIndexToErrChannel() {
        val file = getFile("data/completion_data.txt")
        val output = java.io.ByteArrayOutputStream()
        val err = java.io.ByteArrayOutputStream()
        val separator = com.intellij.stats.events.completion.SessionsInputSeparator(java.io.FileInputStream(file), output, err)
        separator.processInput()
    }

    private fun getFile(path: String): java.io.File {
        return java.io.File(javaClass.classLoader.getResource(path).file)
    }

    @Test
    fun test0() {
        val file = getFile("data/0")
        val output = java.io.ByteArrayOutputStream()
        val err = java.io.ByteArrayOutputStream()
        val separator = com.intellij.stats.events.completion.SessionsInputSeparator(java.io.FileInputStream(file), output, err)
        separator.processInput()
        
        Assertions.assertThat(err.size()).isEqualTo(0)
    }

    @Test
    fun testError0() {
        val file = getFile("data/1")
        val output = java.io.ByteArrayOutputStream()
        val err = java.io.ByteArrayOutputStream()
        val separator = com.intellij.stats.events.completion.SessionsInputSeparator(java.io.FileInputStream(file), output, err)
        separator.processInput()

        Assertions.assertThat(err.size()).isEqualTo(0)
    }

}


class ErrorSessionDumper(input: java.io.InputStream, output: java.io.OutputStream, error: java.io.OutputStream) : com.intellij.stats.events.completion.SessionsInputSeparator(input, output, error) {
    var totalFailedSessions = 0
    var totalSuccessSessions = 0
    private val dir = java.io.File("errors")

    init {
        dir.mkdir()
    }

    override fun onSessionProcessingFinished(session: List<com.intellij.stats.events.completion.EventLine>, isValidSession: Boolean) {
        if (!isValidSession) {
            val file = java.io.File(dir, totalFailedSessions.toString())
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