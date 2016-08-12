package com.intellij.stats.completion.validator

import org.assertj.core.api.Assertions
import org.junit.Test
import java.io.*

class RealTextValidator {
    
    
    @Test
    fun test_NegativeIndexToErrChannel() {
        val file = File("completion_data.txt")
        val output = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()
        val separator = SessionsInputSeparator(FileInputStream(file), output, err)
        separator.processInput()
    }
    
    @Test
    fun test0() {
        val file = File("0")
        val output = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()
        val separator = SessionsInputSeparator(FileInputStream(file), output, err)
        separator.processInput()
        
        Assertions.assertThat(err.size()).isEqualTo(0)
    }
    
    
    @Test
    fun test_DumpErr() {
        val file = File("completion_data.txt")
        val output = ByteArrayOutputStream()
        val err = FileOutputStream(File("err"))
        val separator = ErrorSessionDumper(FileInputStream(file), output, err)         
        separator.processInput()
    }

}


class ErrorSessionDumper(input: InputStream, output: OutputStream, error: OutputStream) : SessionsInputSeparator(input, output, error) {
    private var i = 0
    private val dir = File("errors")

    init {
        dir.mkdir()
    }

    override fun onSessionProcessingFinished(session: List<EventLine>, isValidSession: Boolean) {
        if (!isValidSession) {
            val file = File(dir, i.toString())
            if (file.exists()) {
                file.delete()
            }
            file.createNewFile()

            session.forEach {
                file.appendText(it.line)
                file.appendText("\n")
            }
            i++
        }
    }
}