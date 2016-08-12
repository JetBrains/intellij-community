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
    
    
    
//    @Test
//    fun test_DumpErr() {
//        val file = File("completion_data.txt")
//        val output = ByteArrayOutputStream()
//        val err = FileOutputStream(File("err"))
//        val separator = ErrorSessionDumper(FileInputStream(file), output, err)         
//        separator.processInput()
//    }
    
    private fun validate(file: File, expectedOut: String, expectedErr: String) {
        val output = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()

        val separator = SessionsInputSeparator(FileInputStream(file), output, err)
        separator.processInput()

        Assertions.assertThat(err.toString().trim()).isEqualTo(expectedErr)
        Assertions.assertThat(output.toString().trim()).isEqualTo(expectedOut)
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