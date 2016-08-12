package com.intellij.stats.completion.validator

import org.assertj.core.api.Assertions
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

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
    fun test_DumpErr() {
        val data = File("completion_data.txt")
        val output = ByteArrayOutputStream()
        val err = FileOutputStream(File("err"))
        val separator = SessionsInputSeparator(FileInputStream(data), output, err)
        separator.processInput()
    }
    
    private fun validate(file: File, expectedOut: String, expectedErr: String) {
        val output = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()

        val separator = SessionsInputSeparator(FileInputStream(file), output, err)
        separator.processInput()

        Assertions.assertThat(err.toString().trim()).isEqualTo(expectedErr)
        Assertions.assertThat(output.toString().trim()).isEqualTo(expectedOut)
    }


}