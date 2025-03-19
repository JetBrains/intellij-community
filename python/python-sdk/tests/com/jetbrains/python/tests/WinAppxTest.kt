package com.jetbrains.python.tests

import com.intellij.openapi.util.SystemInfoRt
import com.jetbrains.python.sdk.appxProduct
import com.jetbrains.python.sdk.getAppxFiles
import org.junit.Assert
import org.junit.Assume
import org.junit.Test

class WinAppxTest {
  @Test
  fun testAppx() {
    Assume.assumeTrue("Win only", SystemInfoRt.isWindows)
    val appxFiles = getAppxFiles(null, Regex(".+"))
    Assume.assumeTrue("No appx apps found", appxFiles.isNotEmpty())
    for (appxFile in appxFiles) {
      Assert.assertNotNull("No appx product product for $appxFile", appxFile.appxProduct)
    }
  }
}