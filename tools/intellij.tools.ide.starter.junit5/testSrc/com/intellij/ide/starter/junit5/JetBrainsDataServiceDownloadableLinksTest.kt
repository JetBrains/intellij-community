package com.intellij.ide.starter.junit5

import com.intellij.ide.starter.community.JetBrainsDataServiceClient
import com.intellij.ide.starter.community.ProductInfoRequestParameters
import com.intellij.ide.starter.community.model.Download
import com.intellij.ide.starter.community.model.OperatingSystem
import com.intellij.ide.starter.community.model.ReleaseInfo
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.openapi.util.BuildNumber
import com.intellij.tools.ide.util.common.logOutput
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.longs.shouldNotBeZero
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldNotBeEmpty
import org.junit.jupiter.api.Test

class JetBrainsDataServiceDownloadableLinksTest {
  private fun List<ReleaseInfo>.verifyTheLatestRelease() {
    val theLatestRelease = this.sortedByDescending { BuildNumber.fromString(it.build) }.first()

    logOutput("The latest release that will be verified: $theLatestRelease")
    theLatestRelease.downloads.checkDownloadableLinksArePresent()
  }

  private fun OperatingSystem.checkSizeAndCheckSumArePresent() {
    link.shouldNotBeEmpty()
    size.shouldNotBeZero()
    checksumLink.shouldNotBeEmpty()
  }

  private fun Download.checkDownloadableLinksArePresent() {
    linux.shouldNotBeNull().checkSizeAndCheckSumArePresent()
    linuxArm.shouldNotBeNull().checkSizeAndCheckSumArePresent()
    windows.shouldNotBeNull().checkSizeAndCheckSumArePresent()
    windowsArm.shouldNotBeNull().checkSizeAndCheckSumArePresent()
    windowsZip.shouldNotBeNull().checkSizeAndCheckSumArePresent()
    mac.shouldNotBeNull().checkSizeAndCheckSumArePresent()
    macM1.shouldNotBeNull().checkSizeAndCheckSumArePresent()
  }

  @Test
  fun `get latest releases of IntellijJ`() {
    val latestReleases = JetBrainsDataServiceClient
                           .getReleases(ProductInfoRequestParameters(type = IdeProductProvider.IU.productCode, snapshot = "release"))
                           .values
                           .firstOrNull() ?: listOf()

    latestReleases.shouldNotBeEmpty()
    latestReleases.verifyTheLatestRelease()
  }

  @Test
  fun `get latest preview releases of Fleet`() {
    // TODO: if/when Fleet will make a public release - switch the code to use release channel
    val latestReleases = JetBrainsDataServiceClient
                           .getReleases(ProductInfoRequestParameters(type = "FL", snapshot = "preview"))
                           .values
                           .firstOrNull() ?: listOf()

    latestReleases.shouldNotBeEmpty()
    latestReleases.verifyTheLatestRelease()
  }

  @Test
  fun `get DotTrace releases`() {
    val latestReleases = JetBrainsDataServiceClient
                           .getReleases(ProductInfoRequestParameters(type = "DP", snapshot = "release"))
                           .values
                           .firstOrNull() ?: listOf()

    latestReleases.shouldNotBeEmpty()

    val theLatestRelease = latestReleases.sortedByDescending { BuildNumber.fromString(it.build) }.first()
    logOutput("The latest release that will be verified: $theLatestRelease")

    // No Windows ZIP archive for DotTrace
    theLatestRelease.downloads.apply {
      linux.shouldNotBeNull().checkSizeAndCheckSumArePresent()
      linuxArm.shouldNotBeNull().checkSizeAndCheckSumArePresent()
      windows.shouldNotBeNull().checkSizeAndCheckSumArePresent()
      windowsArm.shouldNotBeNull().checkSizeAndCheckSumArePresent()
      mac.shouldNotBeNull().checkSizeAndCheckSumArePresent()
      macM1.shouldNotBeNull().checkSizeAndCheckSumArePresent()
    }
  }
}