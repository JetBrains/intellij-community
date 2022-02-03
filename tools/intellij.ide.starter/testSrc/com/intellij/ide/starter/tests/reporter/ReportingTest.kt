package com.intellij.ide.starter.tests.reporter

import com.intellij.ide.starter.utils.convertToHashCodeWithOnlyLetters
import com.intellij.ide.starter.utils.generifyErrorMessage
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream


class ReportingTest {

  companion object {
    @JvmStatic
    fun generifyErrorMessageDataProvider(): Stream<Arguments> {
      return Stream.of(
        Arguments.of("text@3ba5aac, text", "text<ID>, text"),
        Arguments.of("some-text.db451f59", "some-text.<HASH>"),
        Arguments.of("0x01", "<HEX>"),
        Arguments.of("text1234text", "text<NUM>text"),
      )
    }
  }

  @ParameterizedTest
  @MethodSource("generifyErrorMessageDataProvider")
  fun generifyErrorMessageTest(originalMessage: String, expectedMessage: String) {
    generifyErrorMessage(originalMessage).shouldBe(expectedMessage)
  }

  @Test
  fun skipNonAlphaNumericSymbolsWhenConvertFromHashCode() {
    // Eg: AEFKQ]H => AEFKQH
    convertToHashCodeWithOnlyLetters(-399429869).shouldBe("AEFKQH")
  }
}