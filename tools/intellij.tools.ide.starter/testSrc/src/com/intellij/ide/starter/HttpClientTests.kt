package com.intellij.ide.starter

import com.intellij.ide.starter.utils.HttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.fail
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.io.path.fileSize
import kotlin.io.path.writeText

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
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  fun downloadShouldReturnFalseWhenAllRetriesFail() {
    // nothing is listening on port 1, so every attempt fails with a connection error, which is retryable
    val url = "http://127.0.0.1:1/non-existing-file"
    val tmpFile = Files.createTempFile("download", "")

    // retries = 2 so that the retry-and-delay branch of withRetry is exercised too, not only the give-up branch
    assertFalse(HttpClient.download(url, tmpFile, retries = 2), "Download of $url should return false")
  }

  @Test
  @Timeout(value = 10, unit = TimeUnit.SECONDS)
  fun downloadShouldBeSuccessful() {
    val url = "https://www.jetbrains.com/favicon.ico"
    val tmpFile = Files.createTempFile("download", "")

    assertTrue(HttpClient.download(url, tmpFile), "Download of $url should be successful")
  }

  @Test
  @Timeout(value = 10, unit = TimeUnit.SECONDS)
  fun `downloadIfMissing does not download if file is not empty`() {
    val tmpFile = Files.createTempFile("download", "")
    tmpFile.writeText("a")

    assertEquals(1, tmpFile.fileSize())
    assertTrue(HttpClient.downloadIfMissing("https://www.jetbrains.com/favicon.ico", tmpFile))
    assertEquals(1, tmpFile.fileSize())
  }

  @Test
  @Timeout(value = 10, unit = TimeUnit.SECONDS)
  fun `downloadIfMissing downloads if file is empty`() {
    val tmpFile = Files.createTempFile("download", "")
    assertEquals(0, tmpFile.fileSize())
    assertTrue(HttpClient.downloadIfMissing("https://www.jetbrains.com/favicon.ico", tmpFile))
    assertNotEquals(0, tmpFile.fileSize())
  }
}