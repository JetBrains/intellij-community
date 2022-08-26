package com.intellij.ide.starter.tests.unit

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.junit5.hyphenateWithClass
import com.intellij.ide.starter.report.FailureDetailsOnCI
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.ide.starter.utils.FileSystem.getFileOrDirectoryPresentableSize
import com.intellij.ide.starter.utils.convertToHashCodeWithOnlyLetters
import com.intellij.ide.starter.utils.formatSize
import com.intellij.ide.starter.utils.generifyErrorMessage
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.kodein.di.direct
import org.kodein.di.instance
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files
import java.util.stream.Stream
import kotlin.io.path.div
import kotlin.random.Random

@ExtendWith(MockitoExtension::class)
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

  @Test
  fun testMeasureConversion() {
    // Eg: AEFKQ]H => AEFKQH
    100.toLong().formatSize().shouldBe("100 B")
    0.toLong().formatSize().shouldBe("0 B")
    1023.toLong().formatSize().shouldBe("1023 B")
    1024.toLong().formatSize().shouldBe("1.0 KB")
    (1024 * 1024).toLong().formatSize().shouldBe("1.0 MB")
    (1024 * 1024 * 1024).toLong().formatSize().shouldBe("1.0 GB")
  }

  @Test
  fun testHash() {
    convertToHashCodeWithOnlyLetters(1232390123).shouldBe("BFJESZ")
    convertToHashCodeWithOnlyLetters(234790123).shouldBe("AKCQIU")
  }

  @Test
  fun testSizeOfFolder() {
    val folder = Files.createTempDirectory(Random.nextInt().toString())
    print(folder)
    val file = File((folder / "test.txt").toString())
    file.writeText("ABC", Charset.defaultCharset())
    file.toPath().getFileOrDirectoryPresentableSize().shouldBe("3 B")
    val file2 = File((folder / "test2.txt").toString())
    file2.writeText("DE", Charset.defaultCharset())
    folder.getFileOrDirectoryPresentableSize().shouldBe("5 B")
  }

  @Mock
  private lateinit var runContextMock: IDERunContext

  @Test
  fun `validate default error failure details generation`(testInfo: TestInfo) {
    val testName = testInfo.hyphenateWithClass()
    Mockito.doReturn(testName).`when`(runContextMock).contextName

    val failureDetails = di.direct.instance<FailureDetailsOnCI>().getFailureDetails(runContext = runContextMock)
    failureDetails.shouldBe("""
      Test: $testName
      You can find logs and other useful info in CI artifacts under the path $testName
    """.trimIndent())
  }
}