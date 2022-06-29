package com.intellij.ide.starter.tests.unit

import com.intellij.ide.starter.community.ProductInfoRequestParameters
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.ide.IdeDownloader
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.tests.examples.data.TestCases
import io.kotest.assertions.withClue
import io.kotest.matchers.longs.shouldNotBeZero
import io.kotest.matchers.paths.shouldExist
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import org.kodein.di.instance
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.fileSize

class IdeDownloaderTest {
  @TempDir
  lateinit var testDirectory: Path

  @Test
  fun completeParametersSetForIdeDownloaderShouldBeCorrectlyCreated() {
    ProductInfoRequestParameters(code = "IC",
                                 type = "eap",
                                 majorVersion = "222",
                                 build = "10.20")
      .toString()
      .shouldBe("?code=IC&type=eap&majorVersion=222&build=10.20")
  }

  @Test
  fun defaultParameterForIdeDownloaderCreation() {
    ProductInfoRequestParameters(code = "IU")
      .toString()
      .shouldBe("?code=IU&type=release")
  }

  @Test
  @Timeout(value = 4, unit = TimeUnit.MINUTES)
  fun downloadEapInstaller() {
    val downloader by di.instance<IdeDownloader>()

    val installer = downloader.downloadIdeInstaller(ideInfo = IdeProductProvider.IC, testDirectory)

    withClue("EAP installer should be downloaded successfully") {
      installer.installerFile.shouldExist()
      installer.installerFile.fileSize().shouldNotBeZero()
    }
  }

  @Test
  @Timeout(value = 4, unit = TimeUnit.MINUTES)
  fun downloadReleaseInstaller() {
    val downloader by di.instance<IdeDownloader>()

    val testCase = TestCases.IC.GradleJitPackSimple.useRelease()
    val installer = downloader.downloadIdeInstaller(ideInfo = testCase.ideInfo, testDirectory)

    withClue("Release installer should be downloaded successfully") {
      installer.installerFile.shouldExist()
      installer.installerFile.fileSize().shouldNotBeZero()
    }
  }
}