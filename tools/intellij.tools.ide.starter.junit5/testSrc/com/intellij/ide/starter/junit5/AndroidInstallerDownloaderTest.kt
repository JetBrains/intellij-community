package com.intellij.ide.starter.junit5

import com.intellij.ide.starter.ide.installer.AndroidInstaller
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotBeEmpty
import org.junit.jupiter.api.Test

class AndroidInstallerDownloaderTest {
  @Test
  fun getLatestAndroidStudioRelease() {
    AndroidInstaller.fetchLatestMajorReleaseVersion().apply {
      version.shouldNotBeEmpty()
      majorVersion.shouldNotBeEmpty()
      version.shouldContain(majorVersion)
      build.shouldNotBeEmpty()
    }
  }
}