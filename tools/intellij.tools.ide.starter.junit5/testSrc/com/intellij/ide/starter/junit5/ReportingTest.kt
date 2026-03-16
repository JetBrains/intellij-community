package com.intellij.ide.starter.junit5

import com.intellij.ide.starter.junit5.config.KillOutdatedProcessesAfterEach
import com.intellij.ide.starter.report.FailureDetailsOnCI
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.ide.starter.utils.FileSystem.getFileOrDirectoryPresentableSize
import com.intellij.ide.starter.utils.convertToHashCodeWithOnlyLetters
import com.intellij.ide.starter.utils.formatSize
import com.intellij.ide.starter.utils.generifyErrorMessage
import com.intellij.ide.starter.utils.replaceSpecialCharactersWithHyphens
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
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
@ExtendWith(KillOutdatedProcessesAfterEach::class)
class ReportingTest {
  private lateinit var currentTestInfo: TestInfo

  companion object {
    @JvmStatic
    fun generifyErrorMessageDataProvider(): Stream<Arguments> {
      return Stream.of(
        Arguments.of("text@3ba5aac, text", "text<ID>, text"),
        Arguments.of("some-text.db451f59", "some-text.<HASH>"),
        Arguments.of("0x01", "<HEX>"),
        Arguments.of("text1234text", "text<NUM>text"),
        Arguments.of("repository-f18efc81", "repository-<NUM>"),
        Arguments.of("repository-cd183e2b", "repository-<NUM>"),
        Arguments.of("cd183e2b-repository", "<NUM>-repository"),
        Arguments.of("repository-cd183e2b-repository", "repository-<NUM>-repository"),
        Arguments.of("Library 'org.jetbrains.kotlin:kotlin-tooling-core:1.9.20-dev-6566' resolution failed", "Library 'org.jetbrains.kotlin:kotlin-tooling-core:<NUM>.<NUM>.<NUM>-dev-<NUM>' resolution failed"),
        Arguments.of("Unhandled exception in [Kernel@vlg56bursheg4flie1tq, Rete(abortOnError=false, commands=capacity=2147483647,data=[onReceive], " +
                     "reteState=kotlinx.coroutines.flow.StateFlowImpl@5ccddd20, dbSource=ReteDbSource(reteState=kotlinx.coroutines.flow.StateFlowImpl@5ccddd20)), " +
                     "DbSourceContextElement(kernel Kernel@vlg56bursheg4flie1tq), ComponentManager(ApplicationImpl@702643297), " +
                     "com.intellij.codeWithMe.ClientIdContextElementPrecursor@1add8c55, CoroutineName(com.intellij.station.core.services.IdeStationServerService), " +
                     "Dispatchers.Default]: D:\\BuildAgent\\temp\\buildTmp\\agentTemp\\Host\\jb.station.ij.8236.sock",
                     "Unhandled exception in [<Kernel details> com.intellij.station.core.services.IdeStationServerService]: D:\\BuildAgent\\temp\\buildTmp\\agentTemp\\Host\\jb.station.ij.<NUM>.sock"),
        Arguments.of("com.intellij.diagnostic.RemoteSerializedThrowable: Invalid lookup start: RangeMarker(invalid:166,166) , " +
                     "EditorImpl[file:///opt/teamcity-agent/temp/buildTmp/testeyqr2monxt4ay/ide-tests/cache/projects/unpacked/laravel.io-8/tests/Unit/UserTest.php], dis",
                     "com.intellij.diagnostic.RemoteSerializedThrowable: Invalid lookup start: RangeMarker(invalid:<NUM>,<NUM>) , EditorImpl[<FILE>], dis"),
      )
    }
  }

  @BeforeEach
  fun beforeEach(testInfo: TestInfo) {
    currentTestInfo = testInfo
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
  fun testReplacingAllSpecialCharacters() {
    " /123 xd/\\fmt::join_view<It, Sentinel, Char> (hot)".replaceSpecialCharactersWithHyphens(listOf())
      .shouldBe("123-xd-fmt-join-view-It-Sentinel-Char-hot")
  }

  @Test
  fun testReplacingSpecialCharactersIgnoringSlash() {
    " /123 xd/\\fmt::join_view<It, Sentinel, Char> (hot)".replaceSpecialCharactersWithHyphens()
      .shouldBe("""/123-xd/\fmt-join-view-It-Sentinel-Char-hot""")
  }

  @Test
  fun relativePathShouldBeUnaffectedAfterReplacingSpecialChars() {
    "some/strange/path with:: ><special < (characters.json".replaceSpecialCharactersWithHyphens()
      .shouldBe("some/strange/path-with-special-characters.json")
  }

  @Test
  fun multiplePassesOfReplacementWorks() {
    "some/strange/path-with-special-characters.json".replaceSpecialCharactersWithHyphens()
      .shouldBe("some/strange/path-with-special-characters.json")

    "/123-xd/-fmt-join-view-it-sentinel-char-hot".replaceSpecialCharactersWithHyphens()
      .shouldBe("/123-xd/-fmt-join-view-it-sentinel-char-hot")
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
  private val ciMessagePrefix = "You can find logs and other useful info in CI artifacts under the path"

  @Test
  fun `validate default error failure details generation`() {
    val testName = currentTestInfo.run {
      testClass.get().name + "." + testMethod.get().name
    }
    Mockito.doReturn(testName).`when`(runContextMock).contextName

    val failureDetails = FailureDetailsOnCI.instance.getFailureDetails(runContext = runContextMock)
    failureDetails.shouldBe("""
      Test: $testName
      $ciMessagePrefix ${testName.replaceSpecialCharactersWithHyphens()}
    """.trimIndent())
  }

  @ValueSource(strings = ["param 1", "param 2"])
  fun `validate parametrized tests error message generation`(param: String) {
    val testName = currentTestInfo.run {
      testClass.get().name + "." + testMethod.get().name + "($param)"
    }
    Mockito.doReturn(testName).`when`(runContextMock).contextName

    val failureDetails = FailureDetailsOnCI.instance.getFailureDetails(runContext = runContextMock)
    failureDetails.shouldBe("""
      Test: $testName
      $ciMessagePrefix ${testName.replaceSpecialCharactersWithHyphens()}
    """.trimIndent())
  }
}