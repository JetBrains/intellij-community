// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit

import com.jetbrains.python.Result
import com.jetbrains.python.mapResult
import org.jetbrains.annotations.Nls
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.io.IOException
import java.nio.ByteBuffer

/**
 * Showcase for [com.jetbrains.python.Result] showing various practices.
 *
 * Say, we need to open a file, read data, and convert it to money using business logic.
 */
class ResultShowCaseTest {
  private companion object {
    @JvmInline
    value class Data(val b: Byte)

    @JvmInline
    value class Money(val dollars: Byte)

    fun openFile(): Result<ByteBuffer, IOException> = Result.Success(ByteBuffer.allocate(1))
    fun readData(bytes: ByteBuffer): Result<Data, IOException> = Result.Success(Data(bytes[0]))
    fun businessLogic(data: Data): Result<Money, @Nls String> = Result.Success(Money(data.b))
  }

  @Test
  fun mappingTest() {
    // Result is either money or first error
    val result = openFile()
      .mapResult { // Same errors, map success only
        readData(it)
      }.mapResultWithErr( // Errors are different: IOException vs. LocalizedErrorString, use mapping
        onSuccess = { businessLogic(it) },
        onErr = { "Oops, ${it.message}" }
      )
    when (result) { // Classic matching
      is Result.Success -> println("Money: ${result.result.dollars}")
      is Result.Failure -> fail("Too bad: ${result.error}")
    }
  }

  @Test
  fun fastReturnTest() {
    fun getData(): Result<Data, IOException> {
      // Get success or return
      val bytes = openFile().getOr { return it }
      return readData(bytes)
    }

    val data = getData().orThrow()
    println(data.b)
  }

  // Long explicit matching
  @Test
  fun explicitMatchingTest() {
    val bytes = when (val r = openFile()) {
      is Result.Failure -> fail("Can't open file $r.error")
      is Result.Success -> r.result
    }

    val data = when (val r = readData(bytes)) {
      is Result.Failure -> fail("Can't read data ${r.error}")
      is Result.Success -> r.result
    }
    when (val r = businessLogic(data)) {
      is Result.Failure -> fail(r.error)
      is Result.Success -> Unit
    }
  }

  // If error is exception it is thrown. Assertion is thrown otherwise
  @Test
  fun testThrowException() {
    val r: Result<Int, IOException> = Result.Failure(IOException("D"))
    Assertions.assertThrowsExactly(IOException::class.java) {
      r.orThrow()
    }

    val r2: Result<Int, Int> = Result.Failure(42)
    Assertions.assertThrowsExactly(AssertionError::class.java) {
      r2.orThrow()
    }
  }
}