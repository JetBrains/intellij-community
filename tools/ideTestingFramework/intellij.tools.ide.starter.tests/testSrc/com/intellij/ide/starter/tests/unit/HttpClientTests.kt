package com.intellij.ide.starter.tests.unit

import com.intellij.ide.starter.utils.HttpClient
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.io.File
import java.util.concurrent.TimeUnit

class HttpClientTests {
  @Test
  @Timeout(value = 1, unit = TimeUnit.MINUTES)
  fun downloadNonExistedFileShouldFailTest() {
    val url = "https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-11_0_15-linux-x64-b2043.56.tar.gz"
    val tmpFile = File.createTempFile("download", "").toPath()

    withClue("Non existing file should return to unsuccessful download") {
      HttpClient.downloadIfMissing(url, tmpFile, retries = 2).shouldBeFalse()
    }
  }

  @Test
  @Timeout(value = 10, unit = TimeUnit.SECONDS)
  fun downloadShouldBeSuccessful() {
    val url = "https://www.jetbrains.com/favicon.ico"
    val tmpFile = File.createTempFile("download", "").toPath()

    withClue("Download should be successful") {
      HttpClient.downloadIfMissing(url, tmpFile).shouldBeTrue()
    }
  }
}