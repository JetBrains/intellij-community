package com.intellij.ide.starter.junit5

import com.intellij.ide.starter.junit5.config.KillOutdatedProcessesAfterEach
import com.intellij.ide.starter.utils.HttpClient
import io.kotest.assertions.withClue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.io.path.fileSize
import kotlin.io.path.writeText

@ExtendWith(KillOutdatedProcessesAfterEach::class)
class HttpClientTests {
  @Test
  @Timeout(value = 1, unit = TimeUnit.MINUTES)
  fun downloadNonExistingFileShouldFailTest() {
    val url = "https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-11_0_15-linux-x64-b2043.56.tar.gz"
    val tmpFile = Files.createTempFile("download", "")

    try {
      HttpClient.download(url, tmpFile, retries = 2)
      fail {
        "Download of $url should fail with ${HttpClient.HttpNotFound::class.java.name} exception"
      }
    }
    catch (t: HttpClient.HttpNotFound) {
      // ok
    }
  }

  @Test
  @Timeout(value = 10, unit = TimeUnit.SECONDS)
  fun downloadShouldBeSuccessful() {
    val url = "https://www.jetbrains.com/favicon.ico"
    val tmpFile = Files.createTempFile("download", "")

    withClue("Download should be successful") {
      HttpClient.download(url, tmpFile)
    }
  }

  @Test
  @Timeout(value = 10, unit = TimeUnit.SECONDS)
  fun `downloadIfMissing does not download if file is not empty`() {
    val tmpFile = Files.createTempFile("download", "")
    tmpFile.writeText("a")

    assertEquals(1, tmpFile.fileSize())
    HttpClient.downloadIfMissing("https://www.jetbrains.com/favicon.ico", tmpFile)
    assertEquals(1, tmpFile.fileSize())
  }

  @Test
  @Timeout(value = 10, unit = TimeUnit.SECONDS)
  fun `downloadIfMissing downloads if file is empty`() {
    val tmpFile = Files.createTempFile("download", "")
    assertEquals(0, tmpFile.fileSize())
    HttpClient.downloadIfMissing("https://www.jetbrains.com/favicon.ico", tmpFile)
    assertNotEquals(0, tmpFile.fileSize())
  }
}